package com.dangdang.domain.comment.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements BaseErrorCode {
    REPLY_DEPTH_EXCEEDED("COMMENT_40001", "답글에는 답글을 달 수 없어요.", HttpStatus.BAD_REQUEST),
    COMMENT_NOT_FOUND("COMMENT_40401", "댓글을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
