package com.offerget.dto.interview;

/**
 * 评分中的亮点：必须引用回答原文。
 */
public record InterviewPointVO(
        String quote,
        String comment) {
}
