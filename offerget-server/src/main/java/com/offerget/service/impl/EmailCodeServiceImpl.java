package com.offerget.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerget.common.BusinessException;
import com.offerget.common.EmailNormalizer;
import com.offerget.config.EmailCodeProperties;
import com.offerget.dto.auth.EmailCodeSentVO;
import com.offerget.entity.AppUser;
import com.offerget.mapper.UserMapper;
import com.offerget.service.EmailCodeService;
import com.offerget.service.MailService;

import lombok.RequiredArgsConstructor;

/**
 * 邮箱验证码：Redis 存储 + 冷却 / 每日 / IP 三道限流，验证码一次性消费。
 */
@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 原子消费验证码：只有当前值与用户输入一致时才删除验证码与错误计数，
     * 避免并发请求用同一个验证码各换一次登录态。
     */
    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return 1
            end
            return 0
            """, Long.class);

    private static final String CODE_PREFIX = "offerget:auth:code:";

    private static final String COOLDOWN_PREFIX = "offerget:auth:cooldown:";

    private static final String ATTEMPT_PREFIX = "offerget:auth:attempt:";

    private static final String DAILY_PREFIX = "offerget:auth:daily:";

    private static final String IP_PREFIX = "offerget:auth:ip:";

    private final StringRedisTemplate redis;

    private final MailService mailService;

    private final EmailCodeProperties properties;

    private final UserMapper userMapper;

    @Override
    public EmailCodeSentVO sendCode(String email, String clientIp) {
        String normalized = EmailNormalizer.normalize(email);

        // 只给已绑定邮箱的账号发码，新邮箱不自动注册
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getEmail, normalized));
        if (user == null) {
            throw BusinessException.badRequest("该邮箱未绑定账号，请先注册或在个人中心绑定邮箱");
        }

        try {
            checkCooldown(normalized);
            checkIpLimit(clientIp);
            checkDailyLimit(normalized);

            String cooldownKey = COOLDOWN_PREFIX + normalized;
            // 先占冷却位，防止并发重复发送
            if (!Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent(cooldownKey, "1", properties.getCooldown()))) {
                throw BusinessException.tooManyRequests("验证码发送过于频繁，请稍后重试");
            }

            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            String codeKey = CODE_PREFIX + normalized;
            try {
                mailService.sendLoginCode(normalized, code, properties.getTtl());
            }
            catch (RuntimeException ex) {
                // 发信失败不占冷却与配额，允许立即重试
                redis.delete(cooldownKey);
                throw ex;
            }

            // 发信成功后再写入验证码并计入配额
            redis.opsForValue().set(codeKey, code, properties.getTtl());
            // 新验证码从头开始计算错误次数，避免旧错误次数让新码一输错就作废
            redis.delete(attemptKey(normalized));
            increment(IP_PREFIX + clientIp, Duration.ofHours(1));
            increment(dailyKey(normalized), Duration.ofHours(24));

            return new EmailCodeSentVO(
                    (int) Math.max(properties.getCooldown().toSeconds(), 1),
                    (int) Math.max(properties.getTtl().toSeconds(), 1));
        }
        catch (DataAccessException ex) {
            log.warn("验证码缓存不可用：{}", ex.getMessage());
            throw BusinessException.serviceUnavailable("验证码服务暂不可用，请确认 Redis 已启动");
        }
    }

    @Override
    public void verify(String email, String code) {
        String normalized = EmailNormalizer.normalize(email);
        String codeKey = CODE_PREFIX + normalized;
        String attemptKey = attemptKey(normalized);

        try {
            String expected = redis.opsForValue().get(codeKey);
            if (expected == null) {
                redis.delete(attemptKey);
                throw BusinessException.badRequest("验证码已过期，请重新获取");
            }
            if (!expected.equals(code)) {
                long attempts = increment(attemptKey, properties.getTtl());
                if (attempts >= properties.getMaxAttempts()) {
                    redis.delete(List.of(codeKey, attemptKey));
                    throw BusinessException.badRequest("验证码错误次数过多，请重新获取");
                }
                throw BusinessException.badRequest("验证码错误，还可尝试 "
                        + (properties.getMaxAttempts() - attempts) + " 次");
            }

            // 原子消费：并发请求只有一个能成功换到登录态
            Long consumed = redis.execute(CONSUME_CODE, List.of(codeKey, attemptKey), code);
            if (consumed == null || consumed == 0L) {
                throw BusinessException.badRequest("验证码已失效，请重新获取");
            }
        }
        catch (DataAccessException ex) {
            log.warn("验证码缓存不可用：{}", ex.getMessage());
            throw BusinessException.serviceUnavailable("验证码服务暂不可用，请确认 Redis 已启动");
        }
    }

    private String attemptKey(String email) {
        return ATTEMPT_PREFIX + email;
    }

    private void checkCooldown(String email) {
        String key = COOLDOWN_PREFIX + email;
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        Long seconds = redis.getExpire(key, TimeUnit.SECONDS);
        long wait = seconds == null || seconds < 0 ? properties.getCooldown().toSeconds() : seconds;
        throw BusinessException.tooManyRequests("验证码发送过于频繁，请 " + Math.max(wait, 1) + " 秒后重试");
    }

    private void checkIpLimit(String clientIp) {
        String value = redis.opsForValue().get(IP_PREFIX + clientIp);
        if (value != null && Integer.parseInt(value) >= properties.getIpHourlyLimit()) {
            throw BusinessException.tooManyRequests("发送次数过多，请稍后再试");
        }
    }

    private void checkDailyLimit(String email) {
        String value = redis.opsForValue().get(dailyKey(email));
        if (value != null && Integer.parseInt(value) >= properties.getDailyLimit()) {
            throw BusinessException.tooManyRequests("该邮箱今日发送次数已达上限，请明天再试");
        }
    }

    /** 首次写入时设置 TTL，之后只累加；窗口从第一次发送开始计算。 */
    private long increment(String key, Duration ttl) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl);
        }
        return count == null ? 0 : count;
    }

    private String dailyKey(String email) {
        return DAILY_PREFIX + email + ":" + LocalDate.now(ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

}
