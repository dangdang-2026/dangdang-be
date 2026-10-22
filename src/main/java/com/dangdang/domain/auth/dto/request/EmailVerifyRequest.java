package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerifyRequest(
        @Schema(description = "인증번호를 받은 이메일", example = "jisu@example.com")
        @NotBlank(message = "이메일을 적어 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        String email,
        @Schema(description = "메일로 받은 6자리 번호", example = "123456")
        @Pattern(regexp = "\\d{6}", message = "인증번호 6자리를 적어 주세요.")
        String code) {}
