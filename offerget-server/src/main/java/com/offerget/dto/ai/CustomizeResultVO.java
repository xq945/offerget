package com.offerget.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

public record CustomizeResultVO(
        Long id,
        Long jobId,
        String jobTitle,
        String company,
        Long resumeId,
        String resumeName,
        String resumeType,
        String status,
        int scoreBefore,
        int scoreAfter,
        List<ResumeSection> sections,
        List<String> warnings,
        String summary,
        LocalDateTime createdAt) {
}
