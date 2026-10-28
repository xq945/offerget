package com.offerget.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("notification_read")
public class NotificationRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String notificationKey;

    private LocalDateTime readAt;

}
