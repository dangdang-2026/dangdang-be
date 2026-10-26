package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailAvailabilityRequest(
        @Schema(description = "가입하려는 이메일", example = "jisu@example.com")
        @NotBlank(message = "이메일을 적어주세요")
        @Email(message ="이메일 형식이 아니에요.")
        String email) {}
