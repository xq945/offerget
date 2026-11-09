package com.offerget.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailCodeLoginRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱最多 120 个字符")
        String email,

        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
        String code) {

    /** 校验注解在构造后执行，先去掉前后空格，避免复制验证码时带空格被误判。 */
    public EmailCodeLoginRequest {
        email = email == null ? null : email.trim();
        code = code == null ? null : code.trim();
    }

}
