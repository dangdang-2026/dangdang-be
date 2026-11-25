package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record ContactMemoUpdateRequest(
        @Schema(description = "연락 기준 메모 (앞뒤 공백을 지운 뒤 100자까지, 한 줄). 비우거나 null이면 지운다",
                example = "같은 시점에 계속 높게 나오면 외래로 전화하기")
        @Pattern(regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$", message = "연락 기준 메모는 한 줄로 적어 주세요.")
        String contactMemo) {

    // 한 줄 검사: 제어 문자(줄바꿈·탭·U+0085)와 줄·문단 구분자(U+2028·U+2029)를 막는다
    // @Valid 검사보다 먼저 실행된다 -> 빈 글은 「지우기」(null)로 바꾸고, 서비스가 공백을 지운 값으로 글자 수를 잰다
    // 글자 수(100자)는 이모지를 한 글자로 세려고 @Size 대신 PregnancyProfileService에서 코드 포인트로 잰다
    public ContactMemoUpdateRequest {
        contactMemo = contactMemo == null || contactMemo.isBlank() ? null : contactMemo.strip();
    }
}
