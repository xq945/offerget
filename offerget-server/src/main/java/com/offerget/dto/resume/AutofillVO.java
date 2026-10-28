package com.offerget.dto.resume;

import java.util.List;

public record AutofillVO(
        Long resumeId,
        String resumeName,
        String name,
        String phone,
        String email,
        String city,
        String school,
        String major,
        String degree,
        String graduationYear,
        List<String> skills,
        List<AutofillExperience> internships,
        List<AutofillExperience> projects) {

    public record AutofillExperience(
            String organization,
            String title,
            String period,
            List<String> bullets) {
    }

}
