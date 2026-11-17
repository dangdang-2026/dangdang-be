package com.dangdang.domain.meal.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MealErrorCode implements BaseErrorCode {
    // 목록 조회 입력 오류
    INVALID_DATE_RANGE("MEAL_40001", "시작 날짜가 끝 날짜보다 늦어요.", HttpStatus.BAD_REQUEST),
    INVALID_CURSOR("MEAL_40002", "cursorAt과 cursorId는 같이 보내 주세요.", HttpStatus.BAD_REQUEST),
    // 수정 입력 오류
    NOTHING_TO_UPDATE("MEAL_40003", "바꿀 값을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),
    // 식사 항목 입력 오류
    ITEM_FOOD_OR_NAME("MEAL_40004", "음식을 고르거나(foodCode) 이름을 적어 주세요. 둘 중 하나만 보내요.", HttpStatus.BAD_REQUEST),
    ITEM_NUTRITION_WITH_FOOD("MEAL_40005", "고른 음식의 영양값은 항목을 추가한 뒤 영양값 직접 입력으로 고쳐 주세요.", HttpStatus.BAD_REQUEST),
    ITEM_NAME_OF_FOOD("MEAL_40006", "고른 음식의 이름은 바꿀 수 없어요. 다른 음식이면 지우고 새로 추가해 주세요.", HttpStatus.BAD_REQUEST),

    // 없음·삭제됨·남의 식사 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    MEAL_NOT_FOUND("MEAL_40401", "식사 기록을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    ITEM_NOT_FOUND("MEAL_40402", "식사 항목을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
