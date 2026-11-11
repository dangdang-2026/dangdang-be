package com.dangdang.domain.reminder.dto.response;

import java.util.List;

public record ReminderPageResponse(List<ReminderResponse> items, int page, int size, boolean hasNext) {
}
