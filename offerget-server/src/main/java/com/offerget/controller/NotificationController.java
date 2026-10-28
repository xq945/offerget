package com.offerget.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.notification.MarkReadRequest;
import com.offerget.dto.notification.NotificationListVO;
import com.offerget.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationListVO> list() {
        return ApiResponse.success(notificationService.list());
    }

    @PutMapping("/read")
    public ApiResponse<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
        notificationService.markRead(request.key());
        return ApiResponse.success();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.success();
    }

}
