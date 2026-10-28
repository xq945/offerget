package com.offerget.common;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 统一响应结构：成功 code = 0，失败使用业务错误码，HTTP 状态码另行设置。
 */
@Data
public class ApiResponse<T> {

    private int code;

    private String message;

    private T data;

    private LocalDateTime timestamp;

    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(0, "success", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

}
