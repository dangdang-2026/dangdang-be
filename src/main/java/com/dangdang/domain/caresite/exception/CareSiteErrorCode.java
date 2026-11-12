package com.dangdang.domain.caresite.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CareSiteErrorCode implements BaseErrorCode {
    // 수정 입력 오류
    NOTHING_TO_UPDATE("CARE_SITE_40001", "바꿀 값을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),
    // 검색 입력 오류
    INVALID_KEYWORD("CARE_SITE_40002", "병원 이름을 2자 이상 50자 이하로 입력해 주세요.", HttpStatus.BAD_REQUEST),

    // 없음·삭제됨·남의 병원 모두 같은 404 (남의 병원이 "있다"는 것도 알려주지 않으려고)
    CARE_SITE_NOT_FOUND("CARE_SITE_40401", "병원을 찾을 수 없어요.", HttpStatus.NOT_FOUND),

    // 심평원 쪽 문제 (키 오류·시간 초과·점검 등)
    HOSPITAL_SEARCH_UNAVAILABLE("CARE_SITE_50201", "병원 검색을 할 수 없어요. 잠시 후 다시 시도하거나 직접 입력해 주세요.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
