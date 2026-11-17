package com.dangdang.domain.food.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FoodErrorCode implements BaseErrorCode {
    FOOD_NOT_FOUND("FOOD_40401", "음식을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
