package com.offerget;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.offerget.service.impl.MailServiceImpl;

/**
 * SMTP 冒烟测试：不启动 Spring 上下文，直接调用生产的 {@link MailServiceImpl}
 * 发送一封验证码邮件，同时覆盖纯文本 + HTML 双版本、发件人与异常转换。
 *
 * <p>QQ 邮箱使用授权码（不是登录密码）。在 Git Bash 中这样运行：</p>
 *
 * <pre>
 * MAIL_USERNAME=你的QQ邮箱 MAIL_PASSWORD=授权码 MAIL_TO=收件邮箱 \
 *     mvn test -Dtest=MailSmokeTest
 * </pre>
 *
 * <p>未提供 MAIL_TO 时自动跳过，不影响常规测试。</p>
 */
class MailSmokeTest {

    @Test
    void sendTestMail() {
        String to = firstNonBlank(env("MAIL_TO"), "mail.to");
        Assumptions.assumeTrue(to != null, "未设置 MAIL_TO，跳过 SMTP 冒烟测试");

        String username = firstNonBlank(env("MAIL_USERNAME"), "mail.username");
        String password = firstNonBlank(env("MAIL_PASSWORD"), "mail.password");
        Assumptions.assumeTrue(username != null && password != null,
                "未设置 MAIL_USERNAME / MAIL_PASSWORD，跳过 SMTP 冒烟测试");

        String host = firstNonBlank(env("MAIL_HOST"), "mail.host");
        String port = firstNonBlank(env("MAIL_PORT"), "mail.port");

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host == null ? "smtp.qq.com" : host);
        sender.setPort(port == null ? 465 : Integer.parseInt(port));
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        MailServiceImpl mailService = new MailServiceImpl(sender, username, password);
        mailService.sendLoginCode(to, "123456", Duration.ofMinutes(5));
    }

    private String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 优先取环境变量，其次取同名的系统属性（便于 -DargLine 传参）。 */
    private String firstNonBlank(String envValue, String propertyName) {
        if (envValue != null) {
            return envValue;
        }
        String value = System.getProperty(propertyName);
        return value == null || value.isBlank() ? null : value.trim();
    }

}
