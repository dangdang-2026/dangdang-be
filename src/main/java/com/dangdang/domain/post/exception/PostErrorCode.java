package com.dangdang.domain.post.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements BaseErrorCode {
    INVALID_CURSOR("POST_40001", "페이지 정보가 올바르지 않아요. 처음부터 다시 불러와 주세요.", HttpStatus.BAD_REQUEST),
    DEFERRED_FILTER("POST_40002", "인기순·기간·대상 필터는 아직 준비 중이에요. 최신순으로 봐 주세요.", HttpStatus.BAD_REQUEST),
    EMPTY_UPDATE("POST_40003", "바꿀 내용을 하나 이상 보내 주세요.", HttpStatus.BAD_REQUEST),
    INVALID_SEARCH("POST_40004", "검색어를 다시 확인해 주세요.", HttpStatus.BAD_REQUEST),
    BLANK_TEXT("POST_40005", "제목과 내용은 비워 둘 수 없어요.", HttpStatus.BAD_REQUEST),
    OFFICIAL_ONLY("POST_40301", "당당 소식은 공식 계정만 쓸 수 있어요.", HttpStatus.FORBIDDEN),
    POST_NOT_FOUND("POST_40401", "글을 찾을 수 없어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
