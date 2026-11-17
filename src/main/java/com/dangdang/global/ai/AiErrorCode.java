package com.dangdang.global.ai;

import com.dangdang.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements BaseErrorCode {
    CONSENT_REQUIRED("AI_40301", "AI 기능을 쓰려면 국외 이전 동의가 필요해요.", HttpStatus.FORBIDDEN),
    DAILY_LIMIT("AI_42901", "오늘은 더 쓸 수 없어요. 내일 다시 해 주세요.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
