package com.dangdang.domain.card.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CardErrorCode implements BaseErrorCode {
    CARD_NOT_FOUND("CARD_40401", "카드를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    WITHDRAWN("CARD_40901", "내려간 카드는 새로 저장할 수 없어요.", HttpStatus.CONFLICT),
    WRONG_STATUS("CARD_40902", "지금 상태에서는 할 수 없어요.", HttpStatus.CONFLICT),
    WITHDRAWN_REACT("CARD_40903", "내려간 카드에는 공감할 수 없어요.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
