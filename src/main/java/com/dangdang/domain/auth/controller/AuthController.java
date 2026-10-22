package com.dangdang.domain.auth.controller;

import com.dangdang.domain.auth.dto.request.EmailCodeRequest;
import com.dangdang.domain.auth.dto.request.EmailVerifyRequest;
import com.dangdang.domain.auth.dto.request.SignupRequest;
import com.dangdang.domain.auth.dto.response.SignupResponse;
import com.dangdang.domain.auth.service.EmailVerificationService;
import com.dangdang.domain.auth.service.SignupService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="Auth", description = "회원가입·로그인 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final SignupService signupService;

    @Operation(
            summary = "[ 사용자 | 토큰 X | 인증번호 받기 ]",
            description =
                    """
                    **Parameters** email: 인증번호를 받을 이메일
                    **Returns** 성공 시 data 없음. 이미 가입된 이메일이면 AUTH_40901, 하루 5번 넘으면 AUTH_42901
                    """)
    @PostMapping("/email/code")
    public ResponseEntity<BaseResponse<Void>> sendCode(
            @Valid @RequestBody EmailCodeRequest request) {
        emailVerificationService.sendCode(request.email());
        return ResponseEntity.ok(BaseResponse.success(200, "인증번호 발송에 성공했습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 X | 인증번호 확인 ]",
            description =
                    """
                    **Parameters** email, code(6자리)
                    **Returns** 성공 시 data 없음. 틀리면 AUTH_40002, 만료·5번 틀림이면 AUTH_40001
                    """)
    @PostMapping("/email/verify")
    public ResponseEntity<BaseResponse<Void>> verify(
            @Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return ResponseEntity.ok(BaseResponse.success(200, "이메일 인증에 성공했습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 X | 회원가입 ]",
            description =
                    """
                    **Parameters** email(인증 완료), displayName(부를 이름), password
                    **Returns** userId. 인증 안 했으면 AUTH_40003, 이미 가입이면 AUTH_40901
                    """)
    @PostMapping("/signup")
    public ResponseEntity<BaseResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        SignupResponse response = new SignupResponse(signupService.signup(request));
        return ResponseEntity.status(201)
                .body(BaseResponse.success(201, "회원가입에 성공했습니다.", response));
    }
}
