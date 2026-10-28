package com.offerget.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

public record OptimizeResultVO(
        Long id,
        Long jobId,
        String jobTitle,
        String company,
        Long resumeId,
        String resumeName,
        int scoreBefore,
        int scoreAfter,
        List<String> addedKeywords,
        List<OptimizeSection> sections,
        String summary,
        LocalDateTime createdAt) {

    /** 按简历段落给出的补充建议 */
    public record OptimizeSection(
            String title,
            String original,
            String suggestion,
            List<String> keywords) {
    }

}
