package com.offerget.dto.auth;

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

        @Min(value = 2024, message = "毕业年份不合法")
        @Max(value = 2035, message = "毕业年份不合法")
        Integer graduationYear) {
}
