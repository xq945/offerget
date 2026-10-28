package com.offerget.dto.crawl;

/**
 * 批量采集时单家公司的执行结果。
 */
public record BatchRunVO(
        String company,
        boolean success,
        int created,
        int updated,
        int skipped,
        String message) {
}
