package com.dangdang.domain.myfood.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MyFoodErrorCode implements BaseErrorCode {
    EMPTY_CHANGE("MYFOOD_40001", "바꿀 내용을 보내 주세요.", HttpStatus.BAD_REQUEST),
    NOT_FOUND("MYFOOD_40401", "내 음식을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    SIMILAR_FOOD_NOT_FOUND("MYFOOD_40402", "비슷한 음식을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    DUPLICATE_NAME("MYFOOD_40901", "같은 이름의 내 음식이 이미 있어요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
