package com.dangdang.domain.meal.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MealErrorCode implements BaseErrorCode {
    // 없음·삭제됨·남의 식사 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    MEAL_NOT_FOUND("MEAL_40401", "식사 기록을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
