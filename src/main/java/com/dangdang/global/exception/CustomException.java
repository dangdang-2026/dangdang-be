package com.dangdang.global.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final BaseErrorCode errorCode;
    private final Object data;   // 실패해도 프론트에 줄 숫자 (예: 남은 초). 없으면 null

    public CustomException(BaseErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }
    // 문구만 바꿔야 할 때 (예: "4번 더 틀리면 …")
    public CustomException(BaseErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }
    public CustomException(BaseErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }
}
