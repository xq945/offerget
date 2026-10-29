package com.offerget.dto.interview;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交作答。skip 为 true 或 answer 为空时按「跳过」处理。
 */
public record AnswerInterviewRequest(
        @NotNull(message = "缺少题目") Long questionId,

        @Size(max = 1000, message = "回答最多 1000 字") String answer,

        Boolean skip) {
}
