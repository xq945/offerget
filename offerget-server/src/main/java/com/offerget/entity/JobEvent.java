package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("job_event")
public class JobEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    /** DEADLINE / APPLY / WRITTEN_TEST / INTERVIEW / RESULT */
    private String eventType;

    private String title;

    private LocalDateTime eventTime;

    private String note;

    private LocalDateTime createdAt;

}
