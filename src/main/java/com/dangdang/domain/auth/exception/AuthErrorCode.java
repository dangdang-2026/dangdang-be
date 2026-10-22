package com.dangdang.domain.auth.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    EMAIL_CODE_EXPIRED("AUTH_40001", "인증번호가 만료됐어요. 다시 받아 주세요.", HttpStatus.BAD_REQUEST),
    EMAIL_CODE_MISMATCH("AUTH_40002", "인증번호가 맞지 않아요.", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED("AUTH_40003", "이메일 인증을 먼저 해 주세요.", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_USED("AUTH_40901", "이미 가입된 이메일이에요.", HttpStatus.CONFLICT),
    EMAIL_SEND_LIMIT(
            "AUTH_42901", "오늘은 인증번호를 더 보낼 수 없어요. 내일 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;

}
