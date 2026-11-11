package com.dangdang.domain.reminder.dto.response;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;

import java.time.Instant;

public record ReminderResponse(
        Long id,
        Long mealId,
        Long readingId,
        MeasureSlot slot,
        Instant scheduledAt,
        ReminderStatus status,
        Instant sentAt,
        Instant openedAt,
        boolean renotified) {

    public static ReminderResponse from(Reminder r) {
        return new ReminderResponse(r.getId(), r.getMealId(), r.getReadingId(), r.getSlot(), r.getScheduledAt(),
                r.getStatus(), r.getSentAt(), r.getOpenedAt(), r.isRenotified());
    }
}
