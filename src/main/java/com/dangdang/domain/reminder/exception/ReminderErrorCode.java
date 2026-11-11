package com.dangdang.domain.reminder.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReminderErrorCode implements BaseErrorCode {
    REMINDER_NOT_FOUND("REMINDER_40401", "알림을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    CANNOT_OPEN("REMINDER_40901", "아직 열 수 없는 알림이에요.", HttpStatus.CONFLICT),
    CANNOT_SNOOZE("REMINDER_40902", "지금은 미룰 수 없는 알림이에요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
