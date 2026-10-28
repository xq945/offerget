package com.offerget.service.impl;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.offerget.ai.AiClient;
import com.offerget.common.BusinessException;
import com.offerget.entity.CrawlWatchCompany;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 采集引擎：按关注公司的官网域名定向搜索，抓取页面后交给模型分类与抽取，
 * 最后做确定性校验。只返回候选，不落库。
 */
@Component
@RequiredArgsConstructor
public class CrawlEngine {

    private static final Logger log = LoggerFactory.getLogger(CrawlEngine.class);

    private static final int MAX_PAGES = 3;

    private static final int SEARCH_COUNT = 10;

    private static final int MIN_PAGE_LENGTH = 300;

    private static final int MAX_CONTENT_LENGTH = 12000;

    private static final Set<String> BATCHES = Set.of("提前批", "正式批", "补录", "其他");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String EXTRACT_PROMPT = """
            你是一名校招信息抽取器。从给定的网页 Markdown 中提取校园招聘信息，输出 JSON。

            第一步：判断页面类型
            - POSITION：岗位详情页或岗位列表页，含具体岗位名与职责
            - PROJECT：招聘公告 / FAQ 页，含招聘流程与投递时间安排
            - EVENT：宣讲会、行程、活动页
            - NAV：首页或导航页，没有实质招聘信息

            第二步：抽取
            - page_type 为 NAV 时，items 返回空数组
            - page_type 为 EVENT 时，只抽取页面里的招聘项目时间安排（投递起止日期等），
              不要抽取宣讲会、行程、活动本身
            - 只抽取与【目标届别】一致的岗位或项目；页面只写其他届别时返回空数组
            - 每个字段必须有 evidence 原文片段（20-100 字）；找不到的字段填 null，禁止推测
            - apply_url 只填页面中明确出现的投递链接，不得使用页面自身 URL
            - batch 只能取：提前批 / 正式批 / 补录 / 其他
            - 日期格式 YYYY-MM-DD；原文没有明确年份就不要猜
            - 项目级信息：info_type=PROJECT，job_title 写项目名（如「2027校园招聘」），deadline 写投递截止日期
            - 岗位级信息：info_type=POSITION，job_title 写岗位名称
            - job_title 必须逐字取自页面原文标题，不要改写、不要简写、不要添加公司名前缀
            - 最多输出 15 条，优先输出截止日期明确的

            输出 JSON：
            {"page_type":"POSITION|PROJECT|EVENT|NAV","items":[{"info_type":"POSITION|PROJECT","job_title":"","batch":"","city":"","open_date":"","deadline":"","apply_url":"","tags":[""],"description":"","evidence":""}],"notes":""}
            只输出 JSON，不要输出其他内容。
            """;

    private final AiClient aiClient;

    private final ObjectMapper objectMapper;

    /**
     * 采集一家关注公司，返回通过校验的候选条目。
     */
    public Outcome crawl(CrawlWatchCompany watch, int graduationYear) {
        String domain = watch.getOfficialDomain();
        if (!StringUtils.hasText(domain)) {
            throw BusinessException.badRequest("请先确认该公司的招聘官网域名");
        }

        String query = watch.getCompany() + " " + graduationYear + " 校园招聘 site:" + domain.trim();
        List<AiClient.SearchResult> results = aiClient.search(query, SEARCH_COUNT);
        List<String> urls = pickUrls(results, domain.trim());
        if (urls.isEmpty()) {
            return new Outcome(results.size(), 0, List.of(), "搜索结果里没有该官网的可采集页面");
        }

        List<ExtractedItem> items = new ArrayList<>();
        int fetched = 0;
        for (String url : urls) {
            String markdown;
            try {
                markdown = aiClient.fetchMarkdown(url);
            }
            catch (BusinessException ex) {
                log.warn("抓取失败，跳过：{}（{}）", url, ex.getMessage());
                continue;
            }
            if (markdown.length() < MIN_PAGE_LENGTH) {
                log.info("页面内容过短，跳过：{}（{} 字）", url, markdown.length());
                continue;
            }
            fetched++;
            try {
                items.addAll(extract(watch, graduationYear, url, markdown));
            }
            catch (BusinessException ex) {
                log.warn("抽取失败，跳过：{}（{}）", url, ex.getMessage());
            }
        }

        String message = fetched == 0
                ? "抓取到的页面都没有实质内容，官网可能需要登录或为动态渲染"
                : "已扫描 " + fetched + " 个页面";
        return new Outcome(results.size(), fetched, dedupe(items), message);
    }

    private List<ExtractedItem> extract(CrawlWatchCompany watch, int graduationYear,
                                        String sourceUrl, String markdown) {
        String user = "【公司】" + watch.getCompany() + "\n"
                + "【目标届别】" + graduationYear + " 届\n"
                + "【来源页】" + sourceUrl + "\n"
                + "【页面内容】\n" + trim(markdown);
        String raw = aiClient.chatJson(EXTRACT_PROMPT, user);
        return parse(raw, sourceUrl, watch);
    }

    private List<ExtractedItem> parse(String raw, String sourceUrl, CrawlWatchCompany watch) {
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(raw, MAP_TYPE);
        }
        catch (Exception ex) {
            log.warn("抽取结果解析失败：{}", ex.getMessage());
            return List.of();
        }

        String pageType = text(root.get("page_type"));
        // EVENT 页面里的宣讲会由 prompt 过滤，但其中的投递时间线仍有价值，继续解析；
        // 只有纯导航页才整页跳过
        if ("NAV".equalsIgnoreCase(pageType)) {
            log.info("页面类型为 {}，已跳过：{}", pageType, sourceUrl);
            return List.of();
        }

        if (!(root.get("items") instanceof List<?> items)) {
            return List.of();
        }

        List<ExtractedItem> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String jobTitle = limit(text(map.get("job_title")), 120);
            if (!StringUtils.hasText(jobTitle)) {
                continue;
            }
            String infoType = "PROJECT".equalsIgnoreCase(text(map.get("info_type")))
                    ? "PROJECT" : "POSITION";
            String batch = normalizeBatch(text(map.get("batch")));
            LocalDate openDate = parseDate(map.get("open_date"));
            LocalDate deadline = parseDate(map.get("deadline"));
            if (openDate != null && deadline != null && openDate.isAfter(deadline)) {
                log.info("开启日期晚于截止日期，丢弃：{}", jobTitle);
                continue;
            }
            String applyUrl = sanitizeApplyUrl(text(map.get("apply_url")), sourceUrl);

            ExtractedItem extracted = new ExtractedItem(
                    infoType,
                    jobTitle,
                    batch,
                    limit(text(map.get("city")), 50),
                    openDate,
                    deadline,
                    applyUrl,
                    tagsOf(map.get("tags")),
                    limit(text(map.get("description")), 500),
                    text(map.get("evidence")),
                    confidenceOf(jobTitle, deadline, applyUrl, map.get("city"), map.get("evidence")),
                    sourceUrl);
            result.add(extracted);
        }
        return result;
    }

    private List<ExtractedItem> dedupe(List<ExtractedItem> items) {
        Map<String, ExtractedItem> unique = new LinkedHashMap<>();
        for (ExtractedItem item : items) {
            String key = item.jobTitle() + "|" + item.batch();
            ExtractedItem existing = unique.get(key);
            if (existing == null || item.confidence() > existing.confidence()) {
                unique.put(key, item);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<String> pickUrls(List<AiClient.SearchResult> results, String domain) {
        List<String> urls = new ArrayList<>();
        for (AiClient.SearchResult result : results) {
            String url = result.url();
            if (!StringUtils.hasText(url) || !isHttp(url)) {
                continue;
            }
            if (!hostMatches(url, domain) || isNoiseUrl(url)) {
                continue;
            }
            if (!urls.contains(url)) {
                urls.add(url);
            }
            if (urls.size() >= MAX_PAGES) {
                break;
            }
        }
        return urls;
    }

    private boolean isHttp(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean hostMatches(String url, String domain) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            return host.equalsIgnoreCase(domain)
                    || host.toLowerCase().endsWith("." + domain.toLowerCase())
                    || domain.toLowerCase().endsWith("." + host.toLowerCase());
        }
        catch (Exception ex) {
            return false;
        }
    }

    private boolean isNoiseUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("/activity")
                || lower.contains("/event")
                || lower.contains("/fair")
                || lower.contains("weixin.qq.com")
                || lower.contains("/wx/");
    }

    private String trim(String markdown) {
        return markdown.length() <= MAX_CONTENT_LENGTH
                ? markdown : markdown.substring(0, MAX_CONTENT_LENGTH);
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String normalizeBatch(String batch) {
        if (batch != null && BATCHES.contains(batch)) {
            return batch;
        }
        return "其他";
    }

    private LocalDate parseDate(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace('/', '-').replace('.', '-');
        if (normalized.length() == 7) {
            normalized += "-01";
        }
        try {
            LocalDate date = LocalDate.parse(normalized);
            LocalDate today = LocalDate.now();
            if (date.isBefore(today.minusDays(30)) || date.isAfter(today.plusDays(400))) {
                return null;
            }
            return date;
        }
        catch (Exception ex) {
            return null;
        }
    }

    private String sanitizeApplyUrl(String applyUrl, String sourceUrl) {
        if (!StringUtils.hasText(applyUrl) || applyUrl.equals(sourceUrl)) {
            return null;
        }
        if (!isHttp(applyUrl)) {
            return null;
        }
        try {
            String host = URI.create(applyUrl).getHost();
            String sourceHost = URI.create(sourceUrl).getHost();
            if (host == null || sourceHost == null) {
                return null;
            }
            return mainDomain(host).equals(mainDomain(sourceHost)) ? applyUrl : null;
        }
        catch (Exception ex) {
            return null;
        }
    }

    private String mainDomain(String host) {
        String[] parts = host.toLowerCase().split("\\.");
        return parts.length >= 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : host;
    }

    private String tagsOf(Object value) {
        if (value instanceof List<?> list) {
            Set<String> tags = new LinkedHashSet<>();
            for (Object item : list) {
                if (item instanceof String tag && !tag.isBlank()) {
                    tags.add(tag.trim());
                }
            }
            return tags.isEmpty() ? null : limit(String.join(",", tags), 200);
        }
        if (value instanceof String text && !text.isBlank()) {
            return limit(text.trim(), 200);
        }
        return null;
    }

    private double confidenceOf(String jobTitle, LocalDate deadline, String applyUrl,
                                Object city, Object evidence) {
        double score = 0.5;
        if (StringUtils.hasText(jobTitle)) {
            score += 0.15;
        }
        if (deadline != null) {
            score += 0.15;
        }
        if (StringUtils.hasText(applyUrl)) {
            score += 0.1;
        }
        if (city instanceof String value && !value.isBlank()) {
            score += 0.05;
        }
        if (evidence instanceof String value && !value.isBlank()) {
            score += 0.05;
        }
        return Math.min(score, 1.0);
    }

    public record ExtractedItem(
            String infoType,
            String jobTitle,
            String batch,
            String city,
            LocalDate openDate,
            LocalDate deadline,
            String applyUrl,
            String tags,
            String description,
            String evidence,
            double confidence,
            String sourceUrl) {
    }

    public record Outcome(int searched, int fetched, List<ExtractedItem> items, String message) {
    }

}
