package com.dangdang.domain.notification.dto.response;

import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        String deepLink,
        Instant sentAt,
        Instant readAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getDeepLink(),
                n.getSentAt(), n.getReadAt());
    }
}
