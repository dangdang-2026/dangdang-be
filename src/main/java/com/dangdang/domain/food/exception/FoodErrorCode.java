package com.dangdang.domain.food.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FoodErrorCode implements BaseErrorCode {
    IMAGE_REQUIRED("FOOD_40001", "사진을 올려 주세요.", HttpStatus.BAD_REQUEST),
    NOT_AN_IMAGE("FOOD_40002", "사진 파일만 올릴 수 있어요. JPG나 PNG로 올려 주세요.", HttpStatus.BAD_REQUEST),
    FOOD_NOT_FOUND("FOOD_40401", "음식을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
