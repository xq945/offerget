package com.offerget.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.offerget.common.ApiResponse;
import com.offerget.dto.profile.ChangePasswordRequest;
import com.offerget.dto.profile.ProfileVO;
import com.offerget.dto.profile.UpdateProfileRequest;
import com.offerget.service.ProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ApiResponse<ProfileVO> detail() {
        return ApiResponse.success(profileService.detail());
    }

    @PutMapping
    public ApiResponse<ProfileVO> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(profileService.update(request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return ApiResponse.success();
    }

}
