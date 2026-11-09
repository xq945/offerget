package com.offerget.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 邮箱验证码的生命周期与发送频率配置。
 */
@Data
@ConfigurationProperties(prefix = "app.auth.email-code")
public class EmailCodeProperties {

    /** 验证码有效期 */
    private Duration ttl = Duration.ofMinutes(5);

    /** 同一邮箱两次发送之间的冷却时间 */
    private Duration cooldown = Duration.ofSeconds(60);

    /** 单个验证码允许的最大错误次数，超过后作废 */
    private int maxAttempts = 5;

    /** 同一邮箱每日发送上限 */
    private int dailyLimit = 10;

    /** 同一 IP 每小时发送上限 */
    private int ipHourlyLimit = 20;

}
