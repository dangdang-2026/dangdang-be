package com.dangdang.domain.push.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PushErrorCode implements BaseErrorCode {
    SUBSCRIPTION_NOT_FOUND("PUSH_40401", "알림 구독을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    NO_ACTIVE_SUBSCRIPTION("PUSH_40402", "알림을 받을 기기가 없어요. 알림을 먼저 허용해 주세요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
