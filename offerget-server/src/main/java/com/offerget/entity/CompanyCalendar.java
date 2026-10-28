package com.offerget.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("company_calendar")
public class CompanyCalendar {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String company;

    private String industry;

    private String city;

    private String jobTitle;

    private String batch;

    /** POSITION / PROJECT */
    private String infoType;

    private LocalDate openDate;

    private LocalDate deadline;

    private String applyUrl;

    private String sourceUrl;

    /** MANUAL / AI */
    private String sourceType;

    private LocalDateTime lastCheckedAt;

    private String tags;

    private String description;

    private String tone;

    private LocalDateTime createdAt;

}
