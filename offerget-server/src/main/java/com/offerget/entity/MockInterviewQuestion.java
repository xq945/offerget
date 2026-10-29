package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * AI 模拟面试题目与作答。追问链、逐题评分以 JSON 存储。
 */
@Data
@TableName("mock_interview_question")
public class MockInterviewQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer seq;

    /** INTRO / PROJECT / TECHNICAL / BEHAVIORAL / PRESSURE / REVERSE */
    private String questionType;

    private String question;

    /** JD / RESUME */
    private String basisSource;

    private String basisQuote;

    private String focus;

    /** EASY / MEDIUM / HARD */
    private String difficulty;

    /** 追问链 JSON：[{question, answer, at}] */
    private String followupsJson;

    private String answer;

    /** 反问时面试官的回答 */
    private String answerFeedback;

    private Integer followupCount;

    /** PENDING / ASKED / ANSWERED / SKIPPED */
    private String status;

    private String scoreJson;

    private String riskFlags;

    private LocalDateTime answeredAt;

}
