package com.offerget.dto.ai;

import jakarta.validation.constraints.NotNull;

public record MatchRequest(
        @NotNull(message = "请选择岗位") Long jobId,
        @NotNull(message = "请选择简历") Long resumeId) {
}
