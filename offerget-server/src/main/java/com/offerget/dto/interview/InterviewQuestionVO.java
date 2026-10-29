package com.offerget.dto.interview;

import java.util.List;

/**
 * 单道题目及其作答、追问与评分。
 */
public record InterviewQuestionVO(
        Long id,
        int seq,
        String type,
        String question,
        String basisSource,
        String basisQuote,
        String focus,
        String difficulty,
        String status,
        String answer,
        List<InterviewFollowUpVO> followups,
        String answerFeedback,
        List<String> riskFlags,
        InterviewScoreVO score) {
}
