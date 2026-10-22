package com.dangdang.global.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {
    INVALID_INPUT("GLOBAL_40001", "입력값을 다시 확인해 주세요.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("GLOBAL_50001", "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

}
