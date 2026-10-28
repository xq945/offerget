package com.offerget.dto.crawl;

/**
 * 域名发现候选：由搜索结果整理，等用户确认后落库。
 */
public record DomainCandidateVO(
        String domain,
        String title,
        String url,
        String reason) {
}
