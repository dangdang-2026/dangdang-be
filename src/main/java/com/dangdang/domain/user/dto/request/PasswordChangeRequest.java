package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @Schema(description = "지금 비밀번호", example = "abcd1234")
        @NotBlank(message = "지금 비밀번호를 적어 주세요.")
        String currentPassword,
        @Schema(description = "새 비밀번호 (8자 이상, 영문·숫자)", example = "newpass123")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상, 영문과 숫자를 섞어 주세요.")
        String newPassword) {}