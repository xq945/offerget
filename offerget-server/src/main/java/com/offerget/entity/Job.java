package com.offerget.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String company;

    private String title;

    private String city;

    private String salaryRange;

    private String jobType;

    private String source;

    private String jdText;

    private LocalDate deadline;

    /** PENDING / APPLIED / WRITTEN_TEST / INTERVIEW / OFFER / REJECTED */
    private String status;

    private Integer matchScore;

    private Long resumeId;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
