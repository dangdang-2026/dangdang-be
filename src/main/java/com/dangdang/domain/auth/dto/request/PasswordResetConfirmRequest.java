package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @Schema(description = "메일 링크 주소 뒤 ?token= 값", example = "q1W2e3...")
        @NotBlank(message = "링크가 올바르지 않아요.")
        String token,
        @Schema(description = "새 비밀번호 (8자 이상, 영문·숫자)", example = "newpass123")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상, 영문과 숫자를 섞어 주세요.")
        String newPassword) {}