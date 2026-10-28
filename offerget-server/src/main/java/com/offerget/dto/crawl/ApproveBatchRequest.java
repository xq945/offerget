package com.offerget.dto.crawl;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ApproveBatchRequest(
        @NotEmpty(message = "请选择要确认的条目")
        List<Long> ids) {
}
