package com.offerget.ai;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.offerget.common.BusinessException;
import com.offerget.config.DeepSeekConfig;

import lombok.RequiredArgsConstructor;

/**
 * AI 能力的统一入口。
 *
 * <p>chat 通过 Spring AI 的 ChatClient（DeepSeekChatModel）调用；search / fetch
 * 是 DeepSeek 专有工具接口，仍走 RestClient。业务语义交给调用方，这里只负责
 * 发请求、取正文、记日志，并把失败统一转成业务异常。</p>
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

    private final ChatClient deepSeekChatClient;

    private final RestClient deepSeekToolsRestClient;

    private final DeepSeekConnectionProperties deepSeekProperties;

    private final DeepSeekChatProperties chatProperties;

    /**
     * 以 JSON 模式调用 chat completions，返回模型输出的原始 JSON 字符串。
     * 配置缺失、网络失败、返回结构异常统一转成业务异常。
     */
    public String chatJson(String systemPrompt, String userPrompt) {
        return chatJson(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)), 0.3);
    }

    /**
     * 多轮消息版本，供模拟面试等需要携带对话历史的场景使用。
     * temperature 由调用方按任务选择：评分偏低、出题偏高。
     */
    public String chatJson(List<Message> messages, double temperature) {
        if (!configured()) {
            throw notConfigured();
        }
        if (messages == null || messages.isEmpty()) {
            throw BusinessException.badRequest("AI 消息不能为空");
        }

        List<org.springframework.ai.chat.messages.Message> aiMessages = messages.stream()
                .map(message -> "system".equals(message.role())
                        ? (org.springframework.ai.chat.messages.Message) new SystemMessage(message.content())
                        : new UserMessage(message.content()))
                .toList();

        // 从默认 options 派生，保留 YAML 中的 model 等配置：
        // DeepSeekChatModel 在 prompt 自带 options 时不会与默认 options 合并
        DeepSeekChatOptions options = chatProperties.toOptions()
                .mutate()
                .temperature(Math.max(0, Math.min(temperature, 2)))
                .maxTokens(MAX_TOKENS)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .build();

        long started = System.currentTimeMillis();
        try {
            ChatResponse response = deepSeekChatClient.prompt(new Prompt(aiMessages, options))
                    .call()
                    .chatResponse();
            long elapsed = System.currentTimeMillis() - started;
            log.info("DeepSeek chat 完成：耗时 {} ms，{}", elapsed, usageOf(response));
            return contentOf(response);
        }
        catch (BusinessException ex) {
            throw ex;
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
        catch (RuntimeException ex) {
            log.warn("DeepSeek chat 调用失败：{}", ex.getMessage());
            throw aiUnavailable();
        }
    }

    /**
     * 搜索网页。site: / after: 等运算符由调用方拼进 query。
     */
    public List<SearchResult> search(String query, int count) {
        if (!configured()) {
            throw notConfigured();
        }
        Map<String, Object> payload = Map.of(
                "query", query,
                "count", Math.max(1, Math.min(count, 50)));

        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = deepSeekToolsRestClient.post()
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
        if (!configured()) {
            throw notConfigured();
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> response = deepSeekToolsRestClient.post()
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

    private boolean configured() {
        return DeepSeekConfig.configured(deepSeekProperties.getApiKey());
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

    private String contentOf(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw aiUnavailable();
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw aiUnavailable();
        }
        return text.trim();
    }

    private String usageOf(ChatResponse response) {
        if (response != null && response.getMetadata() != null
                && response.getMetadata().getUsage() != null) {
            return "tokens=" + response.getMetadata().getUsage().getTotalTokens();
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

    public record Message(String role, String content) {
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
