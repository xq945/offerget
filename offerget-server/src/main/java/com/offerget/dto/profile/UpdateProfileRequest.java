package com.offerget.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "请输入昵称")
        @Size(max = 20, message = "昵称最多 20 个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱最多 120 个字符")
        String email,

        @Min(value = 2024, message = "毕业年份不合法")
        @Max(value = 2035, message = "毕业年份不合法")
        Integer graduationYear) {
}
