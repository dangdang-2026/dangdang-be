package com.dangdang.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record  KakaoLoginResponse(
        @Schema(description = "이번에 처음 가입했는지 (true면 동의 화면 A-01로)", example = "true") boolean newUser) {}
