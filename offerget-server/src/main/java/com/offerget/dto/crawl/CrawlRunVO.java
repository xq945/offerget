package com.offerget.dto.crawl;

/**
 * 一次采集的执行摘要。
 */
public record CrawlRunVO(
        String company,
        int searched,
        int fetched,
        int created,
        int updated,
        int skipped,
        String message) {
}
