package com.offerget.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.interview.AnswerInterviewRequest;
import com.offerget.dto.interview.CreateInterviewRequest;
import com.offerget.dto.interview.InterviewAnswerVO;
import com.offerget.dto.interview.InterviewHistoryVO;
import com.offerget.dto.interview.InterviewReverseVO;
import com.offerget.dto.interview.InterviewSessionVO;
import com.offerget.dto.interview.ReverseInterviewRequest;
import com.offerget.service.MockInterviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI 模拟面试接口。
 */
@RestController
@RequestMapping("/api/mock-interviews")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;

    @PostMapping
    public ApiResponse<InterviewSessionVO> create(@Valid @RequestBody CreateInterviewRequest request) {
        return ApiResponse.success(mockInterviewService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewSessionVO> detail(@PathVariable Long id) {
        return ApiResponse.success(mockInterviewService.detail(id));
    }

    @GetMapping
    public ApiResponse<List<InterviewHistoryVO>> history(
            @RequestParam(required = false) Long jobId) {
        return ApiResponse.success(mockInterviewService.history(jobId));
    }

    @PostMapping("/{id}/answers")
    public ApiResponse<InterviewAnswerVO> answer(@PathVariable Long id,
                                                 @Valid @RequestBody AnswerInterviewRequest request) {
        return ApiResponse.success(mockInterviewService.answer(id, request));
    }

    @PostMapping("/{id}/reverse")
    public ApiResponse<InterviewReverseVO> reverse(@PathVariable Long id,
                                                   @Valid @RequestBody ReverseInterviewRequest request) {
        return ApiResponse.success(mockInterviewService.reverse(id, request));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<InterviewSessionVO> finish(@PathVariable Long id) {
        return ApiResponse.success(mockInterviewService.finish(id));
    }

    @PostMapping("/{id}/abandon")
    public ApiResponse<Void> abandon(@PathVariable Long id) {
        mockInterviewService.abandon(id);
        return ApiResponse.success();
    }

}
