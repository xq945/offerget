package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("resume_customization")
public class ResumeCustomization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    private Long sourceResumeId;

    private Long targetResumeId;

    /** BUILDER / TEXT */
    private String resumeType;

    private String model;

    /** JSON 数组字符串 */
    private String sectionsJson;

    /** JSON 数组字符串 */
    private String warningsJson;

    private Integer scoreBefore;

    private Integer scoreAfter;

    private String summary;

    /** DRAFT / APPLIED / DISCARDED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

}
