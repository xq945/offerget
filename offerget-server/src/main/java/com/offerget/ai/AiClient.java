package com.offerget.ai;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.offerget.common.BusinessException;
import com.offerget.config.DeepSeekProperties;

import lombok.RequiredArgsConstructor;

/**
 * DeepSeek OpenAI 兼容接口的薄封装。
 * 只负责发请求、取 choices[0].message.content、记日志；业务语义交给调用方。
 * 调用前检查密钥配置，避免把未配置状态带到业务逻辑里。
 */
@Component
@RequiredArgsConstructor
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** 简历改写输出较长，默认值容易截断 JSON */
    private static final int MAX_TOKENS = 8192;

    private final RestClient deepSeekRestClient;

    private final DeepSeekProperties properties;

    /**
     * 以 JSON 模式调用 chat completions，返回模型输出的原始 JSON 字符串。
     * 配置缺失、网络失败、返回结构异常统一转成业务异常。
     */
    public String chatJson(String systemPrompt, String userPrompt) {
        if (!properties.configured()) {
            throw BusinessException.badRequest("未配置 DeepSeek 密钥，请先在 application-local.yml 中填写 api-key");
        }

        Map<String, Object> payload = Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"));

        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(MAP_TYPE);
            long elapsed = System.currentTimeMillis() - started;
            log.info("DeepSeek chat 完成：耗时 {} ms，{}", elapsed, usageOf(response));
            return contentOf(response);
        }
        catch (RestClientResponseException ex) {
            log.warn("DeepSeek chat 返回 {}：{}", ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString()));
            throw aiUnavailable();
        }
        catch (RestClientException ex) {
            log.warn("DeepSeek chat 调用失败：{}", ex.getMessage());
            throw aiUnavailable();
        }
    }

    /**
     * 搜索网页。site: / after: 等运算符由调用方拼进 query。
     */
    public List<SearchResult> search(String query, int count) {
        if (!properties.configured()) {
            throw notConfigured();
        }
        Map<String, Object> payload = Map.of(
                "query", query,
                "count", Math.max(1, Math.min(count, 50)));

        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/v1/tools/search")
                    .body(payload)
                    .retrieve()
                    .body(MAP_TYPE);
            log.info("DeepSeek search 完成：query={}，耗时 {} ms", query,
                    System.currentTimeMillis() - started);
            return resultsOf(response);
        }
        catch (RestClientResponseException ex) {
            log.warn("DeepSeek search 返回 {}：{}", ex.getStatusCode().value(),
                    truncate(ex.getResponseBodyAsString()));
            throw aiUnavailable();
        }
        catch (RestClientException ex) {
            log.warn("DeepSeek search 调用失败：{}", ex.getMessage());
            throw aiUnavailable();
        }
    }

    /**
     * 抓取单个网页并转成 Markdown。失败时抛异常，由调用方决定是否跳过。
     */
    public String fetchMarkdown(String url) {
        if (!properties.configured()) {
            throw notConfigured();
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/v1/tools/fetch")
                    .body(Map.of("url", url))
                    .retrieve()
                    .body(MAP_TYPE);
            log.info("DeepSeek fetch 完成：{}，耗时 {} ms", url, System.currentTimeMillis() - started);
            if (response == null || !(response.get("content") instanceof String content)) {
                return "";
            }
            return content;
        }
        catch (RestClientResponseException ex) {
            log.warn("DeepSeek fetch 返回 {}：{}", ex.getStatusCode().value(), url);
            throw aiUnavailable();
        }
        catch (RestClientException ex) {
            log.warn("DeepSeek fetch 调用失败：{} {}", url, ex.getMessage());
            throw aiUnavailable();
        }
    }

    private List<SearchResult> resultsOf(Map<String, Object> response) {
        if (response == null || !(response.get("results") instanceof List<?> items)) {
            return List.of();
        }
        List<SearchResult> results = new java.util.ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                results.add(new SearchResult(
                        stringOf(map.get("title")),
                        stringOf(map.get("url")),
                        stringOf(map.get("snippet")),
                        stringOf(map.get("summary")),
                        stringOf(map.get("date")),
                        intOf(map.get("authority")),
                        doubleOf(map.get("rerank_score"))));
            }
        }
        return results;
    }

    private String stringOf(Object value) {
        return value instanceof String text ? text : null;
    }

    private Integer intOf(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Double doubleOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private BusinessException notConfigured() {
        return BusinessException.badRequest("未配置 DeepSeek 密钥，请先在 application-local.yml 中填写 api-key");
    }

    private String contentOf(Map<String, Object> response) {
        if (response == null) {
            throw aiUnavailable();
        }
        if (!(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw aiUnavailable();
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)) {
            throw aiUnavailable();
        }
        if (!(choice.get("message") instanceof Map<?, ?> message)) {
            throw aiUnavailable();
        }
        if (!(message.get("content") instanceof String text) || text.isBlank()) {
            throw aiUnavailable();
        }
        return text.trim();
    }

    private String usageOf(Map<String, Object> response) {
        if (response != null && response.get("usage") instanceof Map<?, ?> usage) {
            return "tokens=" + usage.get("total_tokens");
        }
        return "tokens=-";
    }

    private BusinessException aiUnavailable() {
        return new BusinessException(50200, "AI 服务暂不可用，请稍后重试", HttpStatus.BAD_GATEWAY);
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    public record SearchResult(
            String title,
            String url,
            String snippet,
            String summary,
            String date,
            Integer authority,
            Double rerankScore) {
    }

}
