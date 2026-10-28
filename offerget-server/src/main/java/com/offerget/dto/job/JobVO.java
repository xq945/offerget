package com.offerget.dto.job;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 岗位视图。列表场景 jdText 为 null，详情场景返回原文。
 */
public record JobVO(
        Long id,
        String company,
        String title,
        String city,
        String salaryRange,
        String jobType,
        String source,
        String jdText,
        LocalDate deadline,
        String status,
        Integer matchScore,
        Long resumeId,
        String resumeName,
        String note,
        Long daysLeft,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
