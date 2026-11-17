package com.dangdang.domain.chat.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {
    CANNOT_RATE("CHAT_40001", "당당이 답에만 평가할 수 있어요.", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_FOUND("CHAT_40401", "대화를 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
