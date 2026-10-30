package com.dangdang.global.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final BaseErrorCode errorCode;

    public CustomException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    // 문구만 바꿔야 할 때 (예: "남은 횟수 2번")
    public CustomException(BaseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
