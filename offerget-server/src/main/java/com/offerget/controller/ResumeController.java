package com.offerget.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.offerget.common.ApiResponse;
import com.offerget.dto.resume.AutofillVO;
import com.offerget.dto.resume.ResumeBuilderRequest;
import com.offerget.dto.resume.ResumeUpdateRequest;
import com.offerget.dto.resume.ResumeVO;
import com.offerget.service.ResumeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping
    public ApiResponse<List<ResumeVO>> list() {
        return ApiResponse.success(resumeService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeVO> detail(@PathVariable Long id) {
        return ApiResponse.success(resumeService.detail(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResumeVO> upload(@RequestPart("file") MultipartFile file,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) String versionLabel) {
        return ApiResponse.success(resumeService.upload(file, name, versionLabel));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResumeVO> update(@PathVariable Long id,
                                        @Valid @RequestBody ResumeUpdateRequest request) {
        return ApiResponse.success(resumeService.update(id, request));
    }

    @PostMapping(value = "/builder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ResumeVO> createBuilder(@Valid @RequestBody ResumeBuilderRequest request) {
        return ApiResponse.success(resumeService.saveBuilder(null, request));
    }

    @PutMapping("/{id}/builder")
    public ApiResponse<ResumeVO> updateBuilder(@PathVariable Long id,
                                               @Valid @RequestBody ResumeBuilderRequest request) {
        return ApiResponse.success(resumeService.saveBuilder(id, request));
    }

    @GetMapping("/{id}/autofill")
    public ApiResponse<AutofillVO> autofill(@PathVariable Long id) {
        return ApiResponse.success(resumeService.autofill(id));
    }

    @PutMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        resumeService.setDefault(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return ApiResponse.success();
    }

}
