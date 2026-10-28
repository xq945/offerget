package com.offerget.dto.notification;

import jakarta.validation.constraints.NotBlank;

public record MarkReadRequest(@NotBlank(message = "提醒标识不能为空") String key) {
}
