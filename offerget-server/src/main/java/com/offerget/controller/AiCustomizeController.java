package com.offerget.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.ai.ApplyCustomizeRequest;
import com.offerget.dto.ai.CustomizeRequest;
import com.offerget.dto.ai.CustomizeResultVO;
import com.offerget.dto.resume.ResumeVO;
import com.offerget.service.ResumeCustomizeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiCustomizeController {

    private final ResumeCustomizeService customizeService;

    @PostMapping("/customize")
    public ApiResponse<CustomizeResultVO> customize(@Valid @RequestBody CustomizeRequest request) {
        return ApiResponse.success(customizeService.customize(request));
    }

    @GetMapping("/customizations")
    public ApiResponse<List<CustomizeResultVO>> history(@RequestParam(required = false) Long jobId) {
        return ApiResponse.success(customizeService.history(jobId));
    }

    @PutMapping("/customizations/{id}/apply")
    public ApiResponse<ResumeVO> apply(@PathVariable Long id,
                                       @Valid @RequestBody ApplyCustomizeRequest request) {
        return ApiResponse.success(customizeService.apply(id, request));
    }

}
