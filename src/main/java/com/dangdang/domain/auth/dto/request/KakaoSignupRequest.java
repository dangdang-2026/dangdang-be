package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoSignupRequest(
        @Schema(description = "카카오 로그인 응답의 kakaoToken (K-08 「처음이에요, 가입할게요」)")
        @NotBlank(message = "카카오 로그인을 다시 해 주세요.") String kakaoToken) {
}
