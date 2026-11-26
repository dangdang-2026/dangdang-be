package com.dangdang.domain.card.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CardErrorCode implements BaseErrorCode {
    IMAGE_REQUIRED("CARD_40001", "올릴 이미지를 골라 주세요.", HttpStatus.BAD_REQUEST),
    NOT_AN_IMAGE("CARD_40002", "PNG나 JPG 이미지만 올릴 수 있어요.", HttpStatus.BAD_REQUEST),
    CARD_NOT_FOUND("CARD_40401", "카드를 찾을 수 없어요.", HttpStatus.NOT_FOUND),
    WITHDRAWN("CARD_40901", "내려간 카드는 새로 저장할 수 없어요.", HttpStatus.CONFLICT),
    WRONG_STATUS("CARD_40902", "지금 상태에서는 할 수 없어요.", HttpStatus.CONFLICT),
    WITHDRAWN_REACT("CARD_40903", "내려간 카드에는 공감할 수 없어요.", HttpStatus.CONFLICT),
    IMAGE_UPLOAD_FAILED("CARD_50201", "이미지를 올리지 못했어요. 잠시 후 다시 해 주세요.", HttpStatus.BAD_GATEWAY),
    IMAGE_STORAGE_OFF("CARD_50301", "이미지 저장소가 꺼져 있어요. 서버의 S3 설정을 확인해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
