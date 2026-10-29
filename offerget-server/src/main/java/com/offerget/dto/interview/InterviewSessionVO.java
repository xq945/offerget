package com.offerget.dto.interview;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟面试会话详情。进行中的会话不返回逐题评分，避免提前看到结果。
 */
public record InterviewSessionVO(
        Long id,
        Long jobId,
        String company,
        String jobTitle,
        Long resumeId,
        String resumeName,
        String interviewType,
        String difficulty,
        String status,
        String opening,
        Integer totalScore,
        InterviewProgressVO progress,
        Long currentQuestionId,
        String currentFollowUp,
        List<InterviewQuestionVO> questions,
        InterviewReportVO report,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt) {
}
