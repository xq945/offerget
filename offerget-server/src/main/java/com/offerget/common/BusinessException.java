package com.offerget.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 业务异常：同时携带业务错误码与 HTTP 状态码。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    private final HttpStatus httpStatus;

    public BusinessException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(40000, message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(40100, message, HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(40400, message, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(40900, message, HttpStatus.CONFLICT);
    }

}
