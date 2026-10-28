package com.offerget.dto.crawl;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WatchCompanyRequest(
        @NotBlank(message = "请输入公司名称")
        @Size(max = 100, message = "公司名称最多 100 个字符")
        String company,

        @Size(max = 200, message = "域名最多 200 个字符")
        String officialDomain,

        @Size(max = 50, message = "行业最多 50 个字符")
        String industry,

        @Size(max = 50, message = "城市最多 50 个字符")
        String city,

        @Min(value = 2020, message = "届别不合法")
        @Max(value = 2100, message = "届别不合法")
        Integer targetGraduationYear) {
}
