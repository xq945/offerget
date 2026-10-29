package com.offerget.dto.interview;

import java.time.LocalDateTime;

/**
 * 模拟面试历史记录摘要。
 */
public record InterviewHistoryVO(
        Long id,
        Long jobId,
        String company,
        String jobTitle,
        String resumeName,
        String interviewType,
        String difficulty,
        String status,
        Integer totalScore,
        int answered,
        int totalQuestions,
        LocalDateTime createdAt,
        LocalDateTime finishedAt) {
}
