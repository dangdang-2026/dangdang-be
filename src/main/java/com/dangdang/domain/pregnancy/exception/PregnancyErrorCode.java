package com.dangdang.domain.pregnancy.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PregnancyErrorCode implements BaseErrorCode {
    FUTURE_DELIVERY_DATE("PREGNANCY_40001", "출산일은 오늘 이후로 고를 수 없어요.", HttpStatus.BAD_REQUEST),
    PAST_ASK_DATE("PREGNANCY_40002", "다시 물어볼 날은 오늘이나 그 뒤로 골라 주세요.", HttpStatus.BAD_REQUEST),
    FUTURE_DIAGNOSIS_DATE("PREGNANCY_40003", "진단일은 오늘 이후로 고를 수 없어요.", HttpStatus.BAD_REQUEST),
    DIAGNOSIS_AFTER_DUE_DATE("PREGNANCY_40004", "진단일은 출산 예정일보다 뒤일 수 없어요.", HttpStatus.BAD_REQUEST),
    WRONG_STATE("PREGNANCY_40901", "지금 상태에서는 할 수 없어요.", HttpStatus.CONFLICT),
    ALREADY_GRADUATED("PREGNANCY_40902", "이미 임신 중 관리를 졸업했어요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
