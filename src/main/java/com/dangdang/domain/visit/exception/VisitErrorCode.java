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
    // AI 정리 입력 오류 (AI 서버가 3000자까지만 받음. 메모 저장은 5000자까지 그대로)
    NOTE_TOO_LONG_FOR_AI("VISIT_40002", "AI 정리는 3000자까지 할 수 있어요. 메모를 나눠서 적어 주세요.", HttpStatus.BAD_REQUEST),
    // 진료용 리포트는 진료 전날까지를 담아서, 진료 날짜가 미정이면 기간을 정할 수 없음
    VISIT_DATE_UNDECIDED("VISIT_40003", "진료 날짜를 먼저 정해 주세요.", HttpStatus.BAD_REQUEST),

    // AI로 정리하는 동안 다른 곳에서 메모가 바뀜 (옛 메모 기준 결과라 저장하지 않음)
    NOTE_CHANGED("VISIT_40901", "정리하는 동안 메모가 바뀌었어요. 다시 정리해 주세요.", HttpStatus.CONFLICT),

    // 없음·삭제됨·남의 일정 모두 같은 404 (남의 기록이 "있다"는 것도 알려주지 않으려고)
    VISIT_NOT_FOUND("VISIT_40401", "진료 일정을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    QUESTION_NOT_FOUND("VISIT_40402", "진료 질문을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    NOTE_NOT_FOUND("VISIT_40403", "진료 메모를 찾을 수 없어요.", HttpStatus.NOT_FOUND),

    // AI 서버 실패·시간 초과·이상한 답 (오늘 횟수는 돌려줌) → 화면에 「다시 해 보기」
    ORGANIZE_FAILED("VISIT_50201", "정리하지 못했어요. 잠시 뒤 다시 해 보세요.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
