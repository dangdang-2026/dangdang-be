package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetConfirmRequest(
        @Schema(description = "메일 링크 주소 뒤 ?token= 값", example = "q1W2e3...")
        @NotBlank(message = "링크가 올바르지 않아요.")
        String token,
        @Schema(description = "비밀번호 8~64자, 영문과 숫자 모두 포함, 특수문자 가능 (한글·띄어쓰기 X)", example = "abcd1234!")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
                message = "비밀번호는 8~64자, 영문과 숫자를 모두 넣어 주세요. 한글·띄어쓰기는 쓸 수 없어요.")
        String newPassword) {}