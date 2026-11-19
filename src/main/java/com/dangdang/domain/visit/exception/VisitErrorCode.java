package com.dangdang.domain.visit.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VisitErrorCode implements BaseErrorCode {
    // 수정 입력 오류
    NOTHING_TO_UPDATE("VISIT_40001", "바꿀 값을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),

    // 없음·삭제됨·남의 일정 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    VISIT_NOT_FOUND("VISIT_40401", "진료 일정을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    QUESTION_NOT_FOUND("VISIT_40402", "진료 질문을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    NOTE_NOT_FOUND("VISIT_40403", "진료 메모를 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
