package com.offerget.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
        String password,

        @NotBlank(message = "请输入昵称")
        @Size(max = 20, message = "昵称最多 20 个字符")
        String nickname,

        // 可选：填写后可用于邮箱验证码登录，全局唯一
        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱最多 120 个字符")
        String email,

        @Min(value = 2024, message = "毕业年份不合法")
        @Max(value = 2035, message = "毕业年份不合法")
        Integer graduationYear) {

    /** 校验注解在构造后执行，先去掉用户名与邮箱的前后空格。 */
    public RegisterRequest {
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim();
    }

}
