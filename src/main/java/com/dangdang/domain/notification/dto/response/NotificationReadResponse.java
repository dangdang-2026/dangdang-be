package com.dangdang.domain.notification.dto.response;

import com.dangdang.domain.notification.entity.Notification;

import java.time.Instant;

public record NotificationReadResponse(Long id, Instant readAt) {

    public static NotificationReadResponse from(Notification n) {
        return new NotificationReadResponse(n.getId(), n.getReadAt());
    }
}
