package com.dangdang.global.common;

import com.dangdang.global.exception.model.BaseErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

// 모든 응답의 봉투. 성공: success·code·message·data / 실패: + errorCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseResponse<T> (boolean success, int code, String message, String errorCode, T data) {

    public static <T> BaseResponse<T> success (int code, String message, T data) {
        return new BaseResponse<>(true, code,message,null,data);
    }

    public static <T> BaseResponse<T> fail(BaseErrorCode errorCode){
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> BaseResponse<T> fail(BaseErrorCode errorCode, String message){
        return new BaseResponse<>(false,errorCode.getStatus().value(), message,errorCode.getCode(), null);
    }

    // 실패해도 숫자를 같이 줄 때 (예: 남은 횟수·남은 초)
    public static <T> BaseResponse<T> fail(BaseErrorCode errorCode, String message, T data) {
        return new BaseResponse<>(false, errorCode.getStatus().value(), message, errorCode.getCode(), data);
    }
}
