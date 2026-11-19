package com.dangdang.domain.report.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {
    CANNOT_REPORT_OWN("REPORT_40001", "내 글이나 댓글은 신고할 수 없어요.", HttpStatus.BAD_REQUEST),
    TARGET_NOT_FOUND("REPORT_40401", "신고 대상을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    ALREADY_HIDDEN("REPORT_40901", "이미 가려져 있어요.", HttpStatus.CONFLICT),
    NOT_HIDDEN("REPORT_40902", "가려져 있지 않아요.", HttpStatus.CONFLICT),
    NOTHING_TO_DISMISS("REPORT_40903", "처리할 접수 신고가 없어요.", HttpStatus.CONFLICT),
    DELETED_TARGET("REPORT_40904", "지운 글이나 댓글은 가리거나 되돌릴 수 없어요. 문제없음으로 정리해 주세요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
