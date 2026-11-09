package com.offerget.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.auth.AuthResponse;
import com.offerget.dto.auth.EmailCodeLoginRequest;
import com.offerget.dto.auth.EmailCodeSentVO;
import com.offerget.dto.auth.LoginRequest;
import com.offerget.dto.auth.RegisterRequest;
import com.offerget.dto.auth.SendEmailCodeRequest;
import com.offerget.dto.auth.UserVO;
import com.offerget.service.AuthService;
import com.offerget.service.EmailCodeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final EmailCodeService emailCodeService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/email-code")
    public ApiResponse<EmailCodeSentVO> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request,
            HttpServletRequest servletRequest) {
        // 不信任 X-Forwarded-For：未部署受信代理时请求头可伪造，会让 IP 限流失效
        return ApiResponse.success(emailCodeService.sendCode(request.email(),
                servletRequest.getRemoteAddr()));
    }

    @PostMapping("/login/email")
    public ApiResponse<AuthResponse> loginByEmailCode(
            @Valid @RequestBody EmailCodeLoginRequest request) {
        return ApiResponse.success(authService.loginByEmailCode(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(authService.currentUser());
    }

}
