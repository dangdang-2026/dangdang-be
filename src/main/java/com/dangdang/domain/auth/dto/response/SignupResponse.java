package com.dangdang.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(@Schema(description = "가입한 회원 번호", example = "1") Long userId) {}
