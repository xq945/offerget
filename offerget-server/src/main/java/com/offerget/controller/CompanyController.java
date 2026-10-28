package com.offerget.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.common.PageQuery;
import com.offerget.common.PageResult;
import com.offerget.dto.company.CompanyVO;
import com.offerget.dto.job.JobVO;
import com.offerget.service.CompanyService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ApiResponse<PageResult<CompanyVO>> page(PageQuery pageQuery,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String industry,
                                                   @RequestParam(required = false) String city) {
        return ApiResponse.success(companyService.page(pageQuery, keyword, industry, city));
    }

    @PostMapping("/{id}/import")
    public ApiResponse<JobVO> importToJobs(@PathVariable Long id) {
        return ApiResponse.success(companyService.importToJobs(id));
    }

}
