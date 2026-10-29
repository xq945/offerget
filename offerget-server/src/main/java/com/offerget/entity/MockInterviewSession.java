package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * AI 模拟面试会话。
 */
@Data
@TableName("mock_interview_session")
public class MockInterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    private Long resumeId;

    /** COMPREHENSIVE / TECHNICAL / BEHAVIORAL */
    private String interviewType;

    /** EASY / MEDIUM / HARD */
    private String difficulty;

    /** IN_PROGRESS / COMPLETED / ABANDONED */
    private String status;

    private String opening;

    private Integer totalScore;

    private String reportJson;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
