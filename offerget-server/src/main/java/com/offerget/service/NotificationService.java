package com.offerget.service;

import com.offerget.dto.notification.NotificationListVO;

public interface NotificationService {

    NotificationListVO list();

    void markRead(String key);

    void markAllRead();

}
