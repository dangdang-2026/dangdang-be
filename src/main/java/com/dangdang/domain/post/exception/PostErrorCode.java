package com.dangdang.domain.post.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements BaseErrorCode {
    INVALID_CURSOR("POST_40001", "페이지 정보가 올바르지 않아요. 처음부터 다시 불러와 주세요.", HttpStatus.BAD_REQUEST),
    LATEST_ONLY("POST_40002", "식단·진료 후기·당당 소식은 최신순으로만 볼 수 있어요.", HttpStatus.BAD_REQUEST),
    EMPTY_UPDATE("POST_40003", "바꿀 내용을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),
    INVALID_SEARCH("POST_40004", "검색어를 다시 확인해 주세요.", HttpStatus.BAD_REQUEST),
    BLANK_TEXT("POST_40005", "제목과 내용은 비워 둘 수 없어요.", HttpStatus.BAD_REQUEST),
    INVALID_POLL_OPTION("POST_40006", "투표 항목을 다시 골라 주세요.", HttpStatus.BAD_REQUEST),
    OFFICIAL_ONLY("POST_40301", "당당 소식은 공식 계정만 쓸 수 있어요.", HttpStatus.FORBIDDEN),
    POST_NOT_FOUND("POST_40401", "글을 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    POLL_NOT_FOUND("POST_40402", "투표가 없는 글이에요.", HttpStatus.NOT_FOUND),
    VOTE_ALREADY_CAST("POST_40901", "이미 다른 항목에 투표했어요. 투표는 바꿀 수 없어요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
