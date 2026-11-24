package com.dangdang.domain.auth.dto.response;

import com.dangdang.domain.auth.infra.EmailVerificationStore;
import io.swagger.v3.oas.annotations.media.Schema;

// 인증번호를 보낸 뒤 화면에 필요한 숫자 (V-01 타이머 · V-08 「0:58 후 다시 받기」 · V-09 「오늘 3번 더」)
public record EmailCodeSendResponse(
        @Schema(description = "오늘 더 받을 수 있는 횟수 (0이면 V-11)", example = "3") int remainingSends,
        @Schema(description = "인증번호 유효 시간(초)", example = "600") long expiresInSeconds,
        @Schema(description = "다시 받기까지 기다릴 초", example = "60") long resendAfterSeconds) {

    public static EmailCodeSendResponse of(long sentToday) {
        int remaining = (int) Math.max(0, EmailVerificationStore.MAX_SENDS_PER_DAY - sentToday);
        return new EmailCodeSendResponse(remaining,
                EmailVerificationStore.CODE_TTL.toSeconds(),
                EmailVerificationStore.RESEND_WAIT.toSeconds());
    }
}
