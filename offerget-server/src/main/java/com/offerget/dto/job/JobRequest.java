package com.offerget.dto.job;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @NotBlank(message = "请输入公司名称")
        @Size(max = 100, message = "公司名称最多 100 个字符")
        String company,

        @NotBlank(message = "请输入岗位名称")
        @Size(max = 120, message = "岗位名称最多 120 个字符")
        String title,

        @Size(max = 50, message = "城市最多 50 个字符")
        String city,

        @Size(max = 50, message = "薪资范围最多 50 个字符")
        String salaryRange,

        String jobType,

        String source,

        String jdText,

        LocalDate deadline,

        Long resumeId,

        @Size(max = 500, message = "备注最多 500 个字符")
        String note) {
}
