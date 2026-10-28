package com.offerget.dto.notification;

import java.util.List;

public record NotificationListVO(long unreadCount, List<NotificationVO> items) {
}
