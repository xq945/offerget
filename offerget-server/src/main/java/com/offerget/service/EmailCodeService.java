package com.offerget.service;

import com.offerget.dto.auth.EmailCodeSentVO;

public interface EmailCodeService {

    /** 校验发送频率与邮箱绑定后，生成验证码并发送邮件。 */
    EmailCodeSentVO sendCode(String email, String clientIp);

    /** 校验并消费验证码；失败时累计错误次数。 */
    void verify(String email, String code);

}
