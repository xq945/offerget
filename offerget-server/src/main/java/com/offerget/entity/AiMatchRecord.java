package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ai_match_record")
public class AiMatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    private Long resumeId;

    private Integer score;

    /** JSON 数组字符串 */
    private String matchedKeywords;

    /** JSON 数组字符串 */
    private String missingKeywords;

    /** JSON 数组字符串 */
    private String suggestions;

    private String summary;

    private LocalDateTime createdAt;

}
