package com.offerget.dto.interview;

import jakarta.validation.constraints.NotNull;

/**
 * 创建模拟面试：岗位与简历必填，类型和难度为空时使用默认值。
 */
public record CreateInterviewRequest(
        @NotNull(message = "请选择岗位") Long jobId,

        @NotNull(message = "请选择简历") Long resumeId,

        String interviewType,

        String difficulty) {
}
