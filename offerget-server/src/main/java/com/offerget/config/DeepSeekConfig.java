package com.offerget.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek 使用的 OpenAI 兼容 HTTP 客户端。
 * 未配置 api-key 时仍会创建 Bean，但不会带鉴权头，调用前请先检查 configured()。
 */
@Configuration
public class DeepSeekConfig {

    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(properties.timeoutSeconds(), 10)));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory);
        if (properties.configured()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.apiKey());
        }
        return builder.build();
    }

}
