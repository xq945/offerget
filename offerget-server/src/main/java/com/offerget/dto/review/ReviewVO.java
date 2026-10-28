package com.offerget.dto.review;

import java.util.List;

public record ReviewVO(
        Funnel funnel,
        List<MonthlyPoint> monthly,
        Integer averageScore,
        List<KeywordCount> missingKeywords,
        List<String> insights) {

    /** 漏斗与各环节转化率（百分比，0-100） */
    public record Funnel(
            long total,
            long applied,
            long writtenTest,
            long interview,
            long offer,
            double applyRate,
            double writtenRate,
            double interviewRate,
            double offerRate) {
    }

    public record MonthlyPoint(String month, String label, long count) {
    }

    public record KeywordCount(String keyword, long count) {
    }

}
