package com.offerget.controller;

import java.util.Map;

import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.config.DeepSeekConfig;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiConfigController {

    private final DeepSeekConnectionProperties connectionProperties;

    private final DeepSeekChatProperties chatProperties;

    /**
     * 仅返回是否配置与模型名，永远不返回 api-key。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.success(Map.of(
                "configured", DeepSeekConfig.configured(connectionProperties.getApiKey()),
                "model", chatProperties.getModel() == null ? "" : chatProperties.getModel(),
                "baseUrl", connectionProperties.getBaseUrl() == null ? "" : connectionProperties.getBaseUrl()));
    }

}
