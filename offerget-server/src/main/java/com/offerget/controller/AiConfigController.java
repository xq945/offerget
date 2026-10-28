package com.offerget.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.config.DeepSeekProperties;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiConfigController {

    private final DeepSeekProperties deepSeekProperties;

    /**
     * 仅返回是否配置与模型名，永远不返回 api-key。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.success(Map.of(
                "configured", deepSeekProperties.configured(),
                "model", deepSeekProperties.model() == null ? "" : deepSeekProperties.model(),
                "baseUrl", deepSeekProperties.baseUrl() == null ? "" : deepSeekProperties.baseUrl()));
    }

}
