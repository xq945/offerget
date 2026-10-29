package com.offerget.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 反问环节：向面试官提问。
 */
public record ReverseInterviewRequest(
        @NotBlank(message = "请输入想问的问题")
        @Size(max = 300, message = "问题最多 300 字") String question) {
}
