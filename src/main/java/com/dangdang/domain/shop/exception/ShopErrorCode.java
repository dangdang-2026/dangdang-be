package com.dangdang.domain.shop.exception;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShopErrorCode implements BaseErrorCode {
    INVALID_CURSOR("SHOP_40001", "페이지 정보가 올바르지 않아요. 처음부터 다시 불러와 주세요.", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND("SHOP_40401", "상품을 찾을 수 없어요. 판매가 끝났을 수 있어요.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
