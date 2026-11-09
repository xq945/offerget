package com.offerget.service;

import java.time.Duration;

public interface MailService {

    /** SMTP 账号与授权码是否已配置。 */
    boolean configured();

    /** 发送登录验证码邮件。 */
    void sendLoginCode(String to, String code, Duration ttl);

}
