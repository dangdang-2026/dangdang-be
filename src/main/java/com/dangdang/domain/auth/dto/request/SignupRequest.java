package com.dangdang.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "인증을 마친 이메일", example = "jisu@example.com")
        @NotBlank(message = "이메일을 적어 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        String email,
        @Schema(description = "부를 이름 (실명 아니어도 됨)", example = "지수")
        @NotBlank(message = "부를 이름을 적어 주세요.")
        @Size(max = 10, message = "부를 이름은 10자까지예요.")
        String displayName,
        @Schema(description = "비밀번호 (8자 이상, 영문·숫자)", example = "abcd1234")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상, 영문과 숫자를 섞어 주세요.")
        String password) {}