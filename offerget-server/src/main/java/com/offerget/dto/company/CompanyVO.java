package com.offerget.dto.company;

import java.time.LocalDate;
import java.util.List;

public record CompanyVO(
        Long id,
        String company,
        String industry,
        String city,
        String jobTitle,
        String batch,
        String infoType,
        LocalDate openDate,
        LocalDate deadline,
        String applyUrl,
        String sourceUrl,
        List<String> tags,
        String description,
        String tone,
        Long daysLeft,
        boolean expired,
        boolean imported) {
}
