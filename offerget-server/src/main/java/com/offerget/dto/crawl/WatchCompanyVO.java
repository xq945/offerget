package com.offerget.dto.crawl;

import java.time.LocalDateTime;

public record WatchCompanyVO(
        Long id,
        String company,
        String officialDomain,
        String industry,
        String city,
        Integer targetGraduationYear,
        boolean enabled,
        LocalDateTime lastCrawledAt,
        long draftCount,
        LocalDateTime createdAt) {
}
