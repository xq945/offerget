package com.offerget.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.ai.MatchRequest;
import com.offerget.dto.ai.MatchResultVO;
import com.offerget.dto.ai.OptimizeRequest;
import com.offerget.dto.ai.OptimizeResultVO;
import com.offerget.service.AiMatchService;
import com.offerget.service.ResumeOptimizeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiMatchController {

    private final AiMatchService aiMatchService;

    private final ResumeOptimizeService resumeOptimizeService;

    @PostMapping("/match")
    public ApiResponse<MatchResultVO> match(@Valid @RequestBody MatchRequest request) {
        return ApiResponse.success(aiMatchService.match(request));
    }

    @GetMapping("/matches")
    public ApiResponse<List<MatchResultVO>> history(@RequestParam(required = false) Long jobId) {
        return ApiResponse.success(aiMatchService.history(jobId));
    }

    @GetMapping("/matches/latest")
    public ApiResponse<MatchResultVO> latest(@RequestParam Long jobId) {
        return ApiResponse.success(aiMatchService.latest(jobId));
    }

    @PostMapping("/optimize")
    public ApiResponse<OptimizeResultVO> optimize(@Valid @RequestBody OptimizeRequest request) {
        return ApiResponse.success(resumeOptimizeService.optimize(request));
    }

    @GetMapping("/optimizations")
    public ApiResponse<List<OptimizeResultVO>> optimizations(@RequestParam(required = false) Long jobId) {
        return ApiResponse.success(resumeOptimizeService.history(jobId));
    }

}
