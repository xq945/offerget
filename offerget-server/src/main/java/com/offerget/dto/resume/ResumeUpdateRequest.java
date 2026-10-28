package com.offerget.dto.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResumeUpdateRequest(
        @NotBlank(message = "请输入简历名称")
        @Size(max = 100, message = "简历名称最多 100 个字符")
        String name,

        @Size(max = 30, message = "版本标签最多 30 个字符")
        String versionLabel) {
}
