package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @Schema(description = "지금 비밀번호", example = "abcd1234")
        @NotBlank(message = "지금 비밀번호를 적어 주세요.")
        String currentPassword,
        @Schema(description = "비밀번호 8~64자, 영문과 숫자 모두 포함, 특수문자 가능 (한글·띄어쓰기 X)", example = "abcd1234!")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
                message = "비밀번호는 8~64자, 영문과 숫자를 모두 넣어 주세요. 한글·띄어쓰기는 쓸 수 없어요.")
        String newPassword) {}