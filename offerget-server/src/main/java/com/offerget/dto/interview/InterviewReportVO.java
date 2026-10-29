package com.offerget.dto.interview;

import java.util.List;
import java.util.Map;

/**
 * 模拟面试报告：由逐题评分本地聚合，总评可选择由模型生成。
 */
public record InterviewReportVO(
        int totalScore,
        Map<String, Integer> dimensions,
        List<InterviewQuestionVO> questions,
        List<String> highlights,
        List<InterviewRiskVO> risks,
        List<InterviewIssueVO> topImprovements,
        List<String> suggestions,
        String summary,
        InterviewReverseReportVO reverse,
        List<InterviewTrendVO> trend) {

    public record InterviewRiskVO(
            String flag,
            List<String> quotes) {
    }

    public record InterviewReverseReportVO(
            List<Item> items,
            String comment) {

        public record Item(String question, String reply) {
        }
    }

    public record InterviewTrendVO(
            String date,
            Integer totalScore) {
    }
}
