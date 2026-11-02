package com.offerget.config;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek 接入配置。
 *
 * <p>chat 走 Spring AI 官方 starter 自动装配的 DeepSeekChatModel / ChatClient；
 * search 与 fetch 是 DeepSeek 专有的工具接口，Spring AI 没有对应抽象，保留 RestClient。</p>
 */
@Configuration
public class DeepSeekConfig {

    /**
     * 未配置密钥时写入 spring.ai.deepseek.api-key 的占位值。
     * Spring AI 自动配置要求 api-key 非空，占位值让应用保持开箱即跑；
     * 业务侧统一用 {@link #configured(String)} 判断，避免把占位值当真实密钥。
     */
    public static final String PLACEHOLDER_API_KEY = "not-configured";

    public static boolean configured(String apiKey) {
        return StringUtils.hasText(apiKey) && !PLACEHOLDER_API_KEY.equals(apiKey.trim());
    }

    /**
     * Spring AI 自动配置通过 ObjectProvider&lt;RestClient.Builder&gt; 取用，借此统一连接/读超时。
     * 声明为 prototype，与 Spring Boot 默认行为一致，避免各消费方互相污染。
     */
    @Bean
    @Scope("prototype")
    public RestClient.Builder deepSeekRestClientBuilder(
            @Value("${app.ai.deepseek.timeout-seconds:60}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(timeoutSeconds, 10)));
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * search / fetch 使用的客户端。未配置密钥时仍然创建，但不带鉴权头，
     * 调用方应先检查 configured()。
     */
    @Bean
    public RestClient deepSeekToolsRestClient(RestClient.Builder deepSeekRestClientBuilder,
            DeepSeekConnectionProperties properties) {
        RestClient.Builder builder = deepSeekRestClientBuilder.clone()
                .baseUrl(properties.getBaseUrl());
        if (configured(properties.getApiKey())) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }
        return builder.build();
    }

    /**
     * 供业务使用的 chat 入口。模板渲染不参与调用：AiClient 直接用 Prompt 构造消息，
     * 避免 prompt 里的 JSON 花括号被当成模板变量。
     */
    @Bean
    public ChatClient deepSeekChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

}
