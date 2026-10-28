package com.offerget.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

public record MatchResultVO(
        Long id,
        Long jobId,
        String jobTitle,
        String company,
        Long resumeId,
        String resumeName,
        Integer score,
        List<String> matched,
        List<String> missing,
        List<String> suggestions,
        String summary,
        LocalDateTime createdAt) {
}
