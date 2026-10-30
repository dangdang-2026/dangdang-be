package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "가입한 이메일", example = "jisu@example.com")
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        String email,

        @Schema(description = "비밀번호", example = "abcd1234")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password) {}