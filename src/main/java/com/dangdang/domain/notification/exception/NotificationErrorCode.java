package com.dangdang.domain.notification.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {
    NOTIFICATION_NOT_FOUND("NOTIFICATION_40401", "알림을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
