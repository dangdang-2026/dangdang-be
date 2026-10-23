package com.dangdang.global.exception;

import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    //일부러 던진 에러
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse<Void>> handleCustomException(CustomException e){
        BaseErrorCode code = e.getErrorCode();
        log.warn("CustomException 발생: {} - {}", code.getCode(), code.getMessage());
        return ResponseEntity.status(code.getStatus()).body(BaseResponse.fail(code,e.getMessage()));
    }

    //@Valid 검증 실패 -> 첫 번째로 틀린 칸의 문구 보여주기
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().get(0);
        log.warn("입력 검증 실패: {}", e.getBindingResult().getFieldErrors());
        GlobalErrorCode code = GlobalErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.getStatus()).body(BaseResponse.fail(code, first.getDefaultMessage()));
    }

    // 에상 못한 에러라면? (내부 내용 숨기고, 고정 문구만)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(Exception e) {
        log.error("예상 못 한 에러", e);
        GlobalErrorCode code = GlobalErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus()).body(BaseResponse.fail(code,e.getMessage()));
    }
}
