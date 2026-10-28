package com.offerget.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("crawl_draft")
public class CrawlDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long watchCompanyId;

    /** POSITION / PROJECT */
    private String infoType;

    private String company;

    private String industry;

    private String city;

    private String jobTitle;

    private String batch;

    private LocalDate openDate;

    private LocalDate deadline;

    private String applyUrl;

    private String sourceUrl;

    private String tags;

    private String description;

    /** 逐字段原文证据 JSON */
    private String evidenceJson;

    private BigDecimal confidence;

    /** DRAFT / APPROVED / REJECTED */
    private String status;

    private LocalDateTime crawledAt;

    private LocalDateTime reviewedAt;

    private Long publishedId;

}
