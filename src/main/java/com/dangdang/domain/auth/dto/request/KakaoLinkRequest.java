package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

// 둘 중 하나만: K-07·K-08에서 받은 kakaoToken, 또는 M-02에서 카카오를 새로 거친 code·redirectUri
public record KakaoLinkRequest(
        @Schema(description = "카카오 로그인 응답의 kakaoToken (L-10)") String kakaoToken,
        @Schema(description = "카카오 인가 코드 (M-02 「연결하기」)") String code,
        @Schema(description = "인가 코드를 받을 때 쓴 리다이렉트 주소") String redirectUri) {

    @AssertTrue(message = "kakaoToken 또는 code·redirectUri 중 하나만 보내 주세요.")
    public boolean isOneWay() {
        return byToken() != byCode();
    }

    public boolean byToken() {
        return kakaoToken != null && !kakaoToken.isBlank();
    }

    public boolean byCode() {
        return code != null && !code.isBlank() && redirectUri != null && !redirectUri.isBlank();
    }
}
