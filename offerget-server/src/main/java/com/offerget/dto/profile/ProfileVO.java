package com.offerget.dto.profile;

import java.time.LocalDateTime;

public record ProfileVO(
        Long id,
        String username,
        String nickname,
        String email,
        Integer graduationYear,
        LocalDateTime createdAt,
        Stats stats) {

    public record Stats(
            long jobCount,
            long resumeCount,
            long matchCount,
            long optimizationCount,
            long daysSinceRegister) {
    }

}
