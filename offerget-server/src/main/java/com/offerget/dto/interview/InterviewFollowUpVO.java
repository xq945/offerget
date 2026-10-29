package com.offerget.dto.interview;

/**
 * 追问记录。at 使用 ISO-8601 字符串，避免 JSON 持久化的时区问题。
 */
public record InterviewFollowUpVO(
        String question,
        String answer,
        String at) {
}
