package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @Schema(description = "카카오가 리다이렉트 주소 뒤에 붙여 준 code (한 번만 쓸 수 있음)", example = "Abc123...")
        @NotBlank(message = "카카오 인가 코드를 보내 주세요.")
        String code,
        @Schema(description = "인가 코드를 받을 때 쓴 리다이렉트 주소 (똑같아야 함)",
                example = "http://localhost:5173/auth/kakao/callback")
        @NotBlank(message = "리다이렉트 주소를 보내 주세요.")
        String redirectUri) {}
