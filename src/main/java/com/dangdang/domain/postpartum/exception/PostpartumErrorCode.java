package com.dangdang.domain.postpartum.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostpartumErrorCode implements BaseErrorCode {
    NOT_POSTPARTUM("POSTPARTUM_40901", "출산일을 먼저 적어 주세요.", HttpStatus.CONFLICT),
    TEST_NOT_FOUND("POSTPARTUM_40401", "검사 일정을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
