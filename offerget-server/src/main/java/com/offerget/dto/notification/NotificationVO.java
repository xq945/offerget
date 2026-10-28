package com.offerget.dto.notification;

/**
 * 一条提醒。key 含日期，按天重置已读状态。
 */
public record NotificationVO(
        String key,
        String type,
        String title,
        String description,
        String level,
        String time,
        Long jobId,
        String company,
        boolean read) {
}
