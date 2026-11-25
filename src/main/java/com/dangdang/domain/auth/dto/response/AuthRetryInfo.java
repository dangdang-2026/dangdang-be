package com.dangdang.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

// 로그인 실패·잠금·인증번호 틀림·메일 대기 때 data에 넣는 숫자 (L-06 · L-07 · V-04 · V-08)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthRetryInfo(Integer remainingAttempts, Long retryAfterSeconds) {

    public static AuthRetryInfo attemptsLeft(int left) {
        return new AuthRetryInfo(left, null);
    }

    public static AuthRetryInfo waitSeconds(long seconds) {
        return new AuthRetryInfo(null, seconds);
    }
}
