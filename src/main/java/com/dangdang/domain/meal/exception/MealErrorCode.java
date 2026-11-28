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
    ITEM_FOOD_OR_NAME("MEAL_40004", "음식을 고르거나(foodCode·productId) 이름을 적어 주세요. 셋 중 하나만 보내요.", HttpStatus.BAD_REQUEST),
    ITEM_NUTRITION_WITH_FOOD("MEAL_40005", "고른 음식의 영양값은 항목을 추가한 뒤 영양값 직접 입력으로 고쳐 주세요.", HttpStatus.BAD_REQUEST),
    ITEM_NAME_OF_FOOD("MEAL_40006", "고른 음식의 이름은 바꿀 수 없어요. 다른 음식이면 지우고 새로 추가해 주세요.", HttpStatus.BAD_REQUEST),
    ITEM_NUTRITION_EMPTY("MEAL_40007", "영양값을 하나 이상 적어 주세요.", HttpStatus.BAD_REQUEST),
    // 식사 수정 입력 오류 — 혈당 쪽 READING_40005(식후 혈당은 식사 시작 뒤)를 식사 쪽에서 지킴
    STARTED_AFTER_READING("MEAL_40008", "이 식사에 붙은 식후 혈당보다 늦게 시작할 수는 없어요.", HttpStatus.BAD_REQUEST),
    // 상점 상품은 영양값이 1개 기준이라 개수(servings)로만 적음
    ITEM_PRODUCT_WEIGHT("MEAL_40009", "상점 상품은 무게 대신 개수로 적어 주세요.", HttpStatus.BAD_REQUEST),
    // 화면을 연 뒤 다른 곳(다른 기기 등)에서 식사가 먼저 바뀜
    MEAL_CHANGED("MEAL_40901", "다른 곳에서 식사가 먼저 바뀌었어요. 새로 불러온 뒤 다시 해 주세요.", HttpStatus.CONFLICT),
    // 직접 적은 음식은 되돌릴 공공 영양값이 없음
    NO_PUBLIC_NUTRITION("MEAL_40902", "직접 적은 음식이라 되돌릴 공공 영양값이 없어요.", HttpStatus.CONFLICT),
    // 되돌리기 (D-08)
    NOT_DELETED("MEAL_40903", "지우지 않은 식사예요.", HttpStatus.CONFLICT),
    UNDO_EXPIRED("MEAL_40904", "지운 지 5분이 지나서 되돌릴 수 없어요.", HttpStatus.CONFLICT),
    // 이번 끼니 건너뛰기 (H-05)
    ALREADY_MEASURED("MEAL_40905", "이미 식후 혈당을 잰 끼니예요.", HttpStatus.CONFLICT),

    // 없음·삭제됨·남의 식사 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    MEAL_NOT_FOUND("MEAL_40401", "식사 기록을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    ITEM_NOT_FOUND("MEAL_40402", "식사 항목을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
