package com.dangdang.domain.insulin.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InsulinErrorCode implements BaseErrorCode {
    // 목록 조회 입력 오류
    INVALID_DATE_RANGE("INSULIN_40001", "시작 날짜가 끝 날짜보다 늦어요.", HttpStatus.BAD_REQUEST),
    // 수정 입력 오류
    NOTHING_TO_UPDATE("INSULIN_40002", "바꿀 값을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),
    // 식사 때 끼니 (E-02 아침·점심·저녁 식사 칩)
    MEAL_SLOT_REQUIRED("INSULIN_40003", "식사 때 인슐린은 아침·점심·저녁 식사 중 하나를 골라 주세요.", HttpStatus.BAD_REQUEST),
    MEAL_SLOT_NOT_ALLOWED("INSULIN_40004", "끼니는 식사 때(MEAL) 인슐린일 때만 보내 주세요.", HttpStatus.BAD_REQUEST),
    SNACK_NOT_ALLOWED("INSULIN_40005", "인슐린 끼니는 아침·점심·저녁 식사 중에서 골라 주세요.", HttpStatus.BAD_REQUEST),

    // 없음·삭제됨·남의 기록 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    INSULIN_LOG_NOT_FOUND("INSULIN_40401", "인슐린 기록을 찾을 수 없어요.", HttpStatus.NOT_FOUND),

    // 화면을 연 뒤 다른 곳(다른 기기 등)에서 먼저 고침 (#172)
    VERSION_CONFLICT("INSULIN_40901", "다른 곳에서 먼저 고쳤어요. 새로고침한 뒤 다시 시도해 주세요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
