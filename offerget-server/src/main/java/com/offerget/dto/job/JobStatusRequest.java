package com.offerget.dto.job;

import jakarta.validation.constraints.NotBlank;

public record JobStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
