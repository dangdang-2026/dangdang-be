package com.dangdang.domain.reminder.dto.response;

import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;

import java.time.Instant;

// openedAt은 열어 보지 않고 바로 저장한 DONE 알림이면 null
public record ReminderOpenResponse(Long id, ReminderStatus status, Instant openedAt) {

    public static ReminderOpenResponse from(Reminder r) {
        return new ReminderOpenResponse(r.getId(), r.getStatus(), r.getOpenedAt());
    }
}
