package com.dangdang.domain.insulin.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InsulinErrorCode implements BaseErrorCode {
    // 수정 입력 오류
    NOTHING_TO_UPDATE("INSULIN_40001", "바꿀 값을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),

    // 없음·삭제됨·남의 기록 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    INSULIN_LOG_NOT_FOUND("INSULIN_40401", "인슐린 기록을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
