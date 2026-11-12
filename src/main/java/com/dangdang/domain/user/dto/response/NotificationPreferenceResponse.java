package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.user.entity.NotificationPreference;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalTime;

public record NotificationPreferenceResponse(
        boolean measurementEnabled,
        boolean scheduleEnabled,
        boolean hideContent,
        boolean renotifyEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime fastingAlarmTime,
        @JsonFormat(pattern = "HH:mm") LocalTime bedtimeAlarmTime,
        Instant updatedAt) {

    public static NotificationPreferenceResponse from(NotificationPreference p) {
        return new NotificationPreferenceResponse(p.isMeasurementEnabled(), p.isScheduleEnabled(), p.isHidePreview(),
                p.isRenotifyEnabled(), p.getFastingAlarmTime(), p.getBedtimeAlarmTime(), p.getUpdatedAt());
    }
}
