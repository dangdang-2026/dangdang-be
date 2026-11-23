package com.dangdang.domain.notification.event;

import com.dangdang.domain.notification.dto.response.NotificationResponse;

// 알림함에 한 줄이 새로 생겼다 — 커밋이 끝나면 앱이 열려 있는 사람에게 바로 띄운다 (앱 안 배너)
public record NotificationCreatedEvent(Long userId, NotificationResponse notification) {
}
