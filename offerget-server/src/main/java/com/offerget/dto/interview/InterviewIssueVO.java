package com.offerget.dto.interview;

/**
 * 评分中的问题点：引用回答原文，severity 为 HIGH / MEDIUM / LOW。
 */
public record InterviewIssueVO(
        String quote,
        String comment,
        String severity) {
}
