package com.offerget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 接入配置。api-key 只从本地配置或环境变量读取，不落源码。
 */
@ConfigurationProperties(prefix = "app.ai.deepseek")
public record DeepSeekProperties(
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

}
