package com.offerget.dto.crawl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CrawlDraftVO(
        Long id,
        Long watchCompanyId,
        String company,
        String infoType,
        String industry,
        String city,
        String jobTitle,
        String batch,
        LocalDate openDate,
        LocalDate deadline,
        String applyUrl,
        String sourceUrl,
        List<String> tags,
        String description,
        String evidence,
        Double confidence,
        String status,
        LocalDateTime crawledAt,
        boolean imported) {
}
