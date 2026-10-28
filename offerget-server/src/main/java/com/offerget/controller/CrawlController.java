package com.offerget.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.crawl.ApproveBatchRequest;
import com.offerget.dto.crawl.BatchRunVO;
import com.offerget.dto.crawl.CrawlDraftVO;
import com.offerget.dto.crawl.CrawlRunVO;
import com.offerget.dto.crawl.DomainCandidateVO;
import com.offerget.dto.crawl.DraftUpdateRequest;
import com.offerget.dto.crawl.WatchCompanyRequest;
import com.offerget.dto.crawl.WatchCompanyVO;
import com.offerget.service.CrawlService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    @GetMapping("/watch-companies")
    public ApiResponse<List<WatchCompanyVO>> listWatch() {
        return ApiResponse.success(crawlService.listWatch());
    }

    @PostMapping("/watch-companies")
    public ApiResponse<WatchCompanyVO> createWatch(@Valid @RequestBody WatchCompanyRequest request) {
        return ApiResponse.success(crawlService.createWatch(request));
    }

    @PutMapping("/watch-companies/{id}")
    public ApiResponse<WatchCompanyVO> updateWatch(@PathVariable Long id,
                                                   @Valid @RequestBody WatchCompanyRequest request) {
        return ApiResponse.success(crawlService.updateWatch(id, request));
    }

    @DeleteMapping("/watch-companies/{id}")
    public ApiResponse<Void> deleteWatch(@PathVariable Long id) {
        crawlService.deleteWatch(id);
        return ApiResponse.success();
    }

    @PostMapping("/watch-companies/{id}/discover-domain")
    public ApiResponse<List<DomainCandidateVO>> discoverDomain(@PathVariable Long id) {
        return ApiResponse.success(crawlService.discoverDomain(id));
    }

    @PostMapping("/watch-companies/{id}/run")
    public ApiResponse<CrawlRunVO> run(@PathVariable Long id) {
        return ApiResponse.success(crawlService.run(id));
    }

    @PostMapping("/run-all")
    public ApiResponse<List<BatchRunVO>> runAll() {
        return ApiResponse.success(crawlService.runAll());
    }

    @GetMapping("/drafts")
    public ApiResponse<List<CrawlDraftVO>> listDrafts(@RequestParam(required = false) String status) {
        return ApiResponse.success(crawlService.listDrafts(status));
    }

    @PutMapping("/drafts/{id}")
    public ApiResponse<CrawlDraftVO> updateDraft(@PathVariable Long id,
                                                 @Valid @RequestBody DraftUpdateRequest request) {
        return ApiResponse.success(crawlService.updateDraft(id, request));
    }

    @PutMapping("/drafts/{id}/approve")
    public ApiResponse<CrawlDraftVO> approve(@PathVariable Long id) {
        return ApiResponse.success(crawlService.approve(id));
    }

    @PutMapping("/drafts/{id}/reject")
    public ApiResponse<CrawlDraftVO> reject(@PathVariable Long id) {
        return ApiResponse.success(crawlService.reject(id));
    }

    @PostMapping("/drafts/approve-batch")
    public ApiResponse<List<CrawlDraftVO>> approveBatch(@Valid @RequestBody ApproveBatchRequest request) {
        return ApiResponse.success(crawlService.approveBatch(request));
    }

}
