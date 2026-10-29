package com.offerget.dto.interview;

import java.util.List;
import java.util.Map;

/**
 * 单题评分。维度允许为空（如反问题只评部分维度）。
 */
public record InterviewScoreVO(
        Map<String, Integer> dimensions,
        List<InterviewPointVO> highlights,
        List<InterviewIssueVO> issues,
        List<String> suggestions,
        List<String> riskFlags,
        Double confidence) {
}
