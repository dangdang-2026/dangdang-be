package com.dangdang.domain.announcement.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnnouncementErrorCode implements BaseErrorCode {
    INVALID_PERIOD("ANNOUNCEMENT_40001", "끝 시각은 시작보다 뒤여야 해요.", HttpStatus.BAD_REQUEST),
    ANNOUNCEMENT_NOT_FOUND("ANNOUNCEMENT_40401", "소식 카드를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    CARD_NOT_LIVE("ANNOUNCEMENT_40901", "올라가 있는 카드만 소식으로 걸 수 있어요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
