package com.offerget.dto.resume;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResumeBuilderRequest(
        @NotBlank(message = "请输入简历名称")
        @Size(max = 100, message = "简历名称最多 100 个字符")
        String name,

        @Size(max = 30, message = "版本标签最多 30 个字符")
        String versionLabel,

        BasicInfo basic,

        List<EducationItem> educations,

        List<ExperienceItem> internships,

        List<ExperienceItem> projects,

        List<String> skills,

        @Size(max = 1000, message = "自我评价最多 1000 个字符")
        String summary) {

    public record BasicInfo(
            @Size(max = 50, message = "姓名最多 50 个字符") String name,
            @Size(max = 30, message = "电话最多 30 个字符") String phone,
            @Size(max = 120, message = "邮箱最多 120 个字符") String email,
            @Size(max = 50, message = "城市最多 50 个字符") String city,
            @Size(max = 100, message = "求职意向最多 100 个字符") String jobIntention) {
    }

    public record EducationItem(
            @Size(max = 100) String school,
            @Size(max = 100) String major,
            @Size(max = 30) String degree,
            @Size(max = 30) String startDate,
            @Size(max = 30) String endDate,
            @Size(max = 500) String description) {
    }

    public record ExperienceItem(
            @Size(max = 100) String organization,
            @Size(max = 100) String title,
            @Size(max = 30) String startDate,
            @Size(max = 30) String endDate,
            List<String> bullets) {
    }

}
