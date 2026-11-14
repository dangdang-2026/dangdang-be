package com.dangdang.global.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {
    INVALID_INPUT("GLOBAL_40001", "입력값을 다시 확인해 주세요.", HttpStatus.BAD_REQUEST),
    NOT_FOUND("GLOBAL_40401", "없는 주소예요.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("GLOBAL_40501", "이 주소에서 쓸 수 없는 요청 방식이에요.", HttpStatus.METHOD_NOT_ALLOWED),
    PAYLOAD_TOO_LARGE("GLOBAL_41301", "파일이 너무 커요.", HttpStatus.CONTENT_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE("GLOBAL_41501", "보내는 형식(Content-Type)을 확인해 주세요.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_SERVER_ERROR("GLOBAL_50001", "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

}
