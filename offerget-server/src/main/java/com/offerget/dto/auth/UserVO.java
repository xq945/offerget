package com.offerget.dto.auth;

public record UserVO(
        Long id,
        String username,
        String nickname,
        String email,
        String avatar,
        Integer graduationYear) {
}
