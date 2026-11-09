package com.offerget.service.impl;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.offerget.common.BusinessException;
import com.offerget.service.MailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * SMTP 邮件发送。未配置账号时应用照常启动，调用方会收到明确的 503 提示。
 *
 * <p>构造器显式注入，便于冒烟测试直接复用生产发送逻辑。</p>
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender mailSender;

    private final String username;

    private final String password;

    public MailServiceImpl(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean configured() {
        return StringUtils.hasText(username) && StringUtils.hasText(password);
    }

    @Override
    public void sendLoginCode(String to, String code, Duration ttl) {
        if (!configured()) {
            throw BusinessException.serviceUnavailable(
                    "邮件服务未配置，请在 application-local.yml 中填写 MAIL_USERNAME / MAIL_PASSWORD");
        }
        long minutes = Math.max(1, ttl.toMinutes());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true：同时设置纯文本与 HTML 备选内容时是必需的
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(username);
            helper.setTo(to);
            helper.setSubject("OfferGet 登录验证码");
            helper.setText(text(code, minutes), html(code, minutes));
            mailSender.send(message);
            log.info("登录验证码邮件已发送：{}", mask(to));
        }
        catch (MailException | MessagingException | IllegalStateException ex) {
            // 不把验证码写进日志
            log.warn("登录验证码邮件发送失败：{}，原因：{}", mask(to), ex.getMessage());
            throw BusinessException.serviceUnavailable("验证码邮件发送失败，请稍后重试");
        }
    }

    private String text(String code, long minutes) {
        return """
                你正在登录 OfferGet，验证码：%s

                验证码 %d 分钟内有效。如非本人操作，请忽略这封邮件。
                """.formatted(code, minutes);
    }

    private String html(String code, long minutes) {
        return """
                <div style="margin:0;padding:32px;background:#f8fbff;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#0f172a;">
                  <div style="max-width:480px;margin:0 auto;padding:32px;border:1px solid #dbeafe;border-radius:16px;background:#ffffff;">
                    <h2 style="margin:0 0 12px;font-size:18px;color:#0f172a;">OfferGet 登录验证码</h2>
                    <p style="margin:0 0 20px;font-size:14px;color:#475569;">你正在登录 OfferGet，请在页面中输入以下验证码：</p>
                    <p style="margin:0 0 20px;font-size:32px;font-weight:700;letter-spacing:8px;color:#2563eb;">%s</p>
                    <p style="margin:0;font-size:12px;color:#94a3b8;">验证码 %d 分钟内有效。如非本人操作，请忽略这封邮件。</p>
                  </div>
                </div>
                """.formatted(code, minutes);
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

}
