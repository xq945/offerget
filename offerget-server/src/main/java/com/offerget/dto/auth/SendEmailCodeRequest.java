package com.offerget.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendEmailCodeRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱最多 120 个字符")
        String email) {

    /** 校验注解在构造后执行，先去掉前后空格，避免 " a@b.com " 被 @Email 误判。 */
    public SendEmailCodeRequest {
        email = email == null ? null : email.trim();
    }

}
