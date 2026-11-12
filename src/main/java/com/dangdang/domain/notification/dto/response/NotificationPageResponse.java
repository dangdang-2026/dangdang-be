package com.dangdang.domain.notification.dto.response;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, int page, int size, boolean hasNext) {
}
