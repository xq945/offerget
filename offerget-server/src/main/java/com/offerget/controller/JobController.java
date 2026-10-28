package com.offerget.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.common.PageQuery;
import com.offerget.common.PageResult;
import com.offerget.dto.job.JobRequest;
import com.offerget.dto.job.JobStatusRequest;
import com.offerget.dto.job.JobVO;
import com.offerget.service.JobService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping
    public ApiResponse<PageResult<JobVO>> page(PageQuery pageQuery,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(jobService.page(pageQuery, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobVO> detail(@PathVariable Long id) {
        return ApiResponse.success(jobService.detail(id));
    }

    @PostMapping
    public ApiResponse<JobVO> create(@Valid @RequestBody JobRequest request) {
        return ApiResponse.success(jobService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobVO> update(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return ApiResponse.success(jobService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable Long id,
                                          @Valid @RequestBody JobStatusRequest request) {
        jobService.changeStatus(id, request.status());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return ApiResponse.success();
    }

}
