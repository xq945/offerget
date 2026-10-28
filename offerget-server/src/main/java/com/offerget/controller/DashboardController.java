package com.offerget.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.dashboard.DashboardVO;
import com.offerget.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ApiResponse<DashboardVO> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/calendar")
    public ApiResponse<List<DashboardVO.CalendarEvent>> calendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate today = LocalDate.now();
        return ApiResponse.success(dashboardService.calendar(
                year == null ? today.getYear() : year,
                month == null ? today.getMonthValue() : month));
    }

}
