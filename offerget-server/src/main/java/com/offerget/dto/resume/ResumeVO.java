package com.offerget.dto.resume;

import java.time.LocalDateTime;

/**
 * 简历视图。列表只带 200 字预览，详情带全文。
 */
public record ResumeVO(
        Long id,
        String name,
        String versionLabel,
        String fileName,
        Long fileSize,
        Boolean isDefault,
        String contentPreview,
        String contentText,
        String contentJson,
        String fileUrl,
        LocalDateTime createdAt) {
}
