package com.offerget.dto.crawl;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

public record DraftUpdateRequest(
        String infoType,

        @Size(max = 50, message = "行业最多 50 个字符")
        String industry,

        @Size(max = 50, message = "城市最多 50 个字符")
        String city,

        @Size(max = 120, message = "岗位名称最多 120 个字符")
        String jobTitle,

        @Size(max = 30, message = "批次最多 30 个字符")
        String batch,

        LocalDate openDate,

        LocalDate deadline,

        @Size(max = 300, message = "投递链接最多 300 个字符")
        String applyUrl,

        @Size(max = 200, message = "标签最多 200 个字符")
        String tags,

        @Size(max = 500, message = "说明最多 500 个字符")
        String description) {
}
