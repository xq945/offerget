package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("crawl_watch_company")
public class CrawlWatchCompany {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String company;

    private String officialDomain;

    private String industry;

    private String city;

    private Integer targetGraduationYear;

    private Boolean enabled;

    private LocalDateTime lastCrawledAt;

    private LocalDateTime createdAt;

}
