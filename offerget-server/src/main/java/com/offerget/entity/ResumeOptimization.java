package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("resume_optimization")
public class ResumeOptimization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    private Long resumeId;

    private Integer scoreBefore;

    private Integer scoreAfter;

    /** JSON 数组字符串 */
    private String addedKeywords;

    /** JSON 数组字符串 */
    private String sections;

    private String summary;

    private LocalDateTime createdAt;

}
