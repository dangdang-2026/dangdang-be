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
        @Schema(description = "비밀번호 8~64자, 영문과 숫자 모두 포함, 특수문자 가능 (한글·띄어쓰기 X)", example = "abcd1234!")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
                message = "비밀번호는 8~64자, 영문과 숫자를 모두 넣어 주세요. 한글·띄어쓰기는 쓸 수 없어요.")
        String password) {}