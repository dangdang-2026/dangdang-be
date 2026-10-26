package com.dangdang.domain.auth.controller;

import com.dangdang.domain.auth.dto.request.LoginRequest;
import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.auth.service.*;
import com.dangdang.global.security.jwt.JwtCookieWriter;
import com.dangdang.global.security.jwt.JwtProvider;
import com.dangdang.global.security.jwt.TokenType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import com.dangdang.domain.auth.dto.request.EmailCodeRequest;
import com.dangdang.domain.auth.dto.request.EmailVerifyRequest;
import com.dangdang.domain.auth.dto.request.SignupRequest;
import com.dangdang.domain.auth.dto.response.SignupResponse;
import com.dangdang.domain.auth.dto.request.KakaoLoginRequest;
import com.dangdang.domain.auth.dto.response.KakaoLoginResponse;
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
    private final LoginService loginService;
    private final KakaoLoginService kakaoLoginService;
    private final TokenService tokenService;
    private final JwtProvider jwtProvider;
    private final JwtCookieWriter jwtCookieWriter;

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

    @Operation(
            summary = "[ 사용자 | 토큰 X | 로그인 ]",
            description =
                    """
                    **Parameters** email, password
                    **Returns (쿠키)** ACCESS_TOKEN, REFRESH_TOKEN. 틀리면 AUTH_40004(남은 횟수), 5번 틀리면 AUTH_42902(5분 잠금), 카카오 계정이면 AUTH_40005
                    """)
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<Void>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = loginService.login(request);
        return ResponseEntity.ok()
                .headers(tokenCookies(tokens))
                .body(BaseResponse.success(200, "로그인에 성공했습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 X | 카카오 로그인 ]",
            description =
                    """
                    **Parameters** code(카카오가 준 인가 코드), redirectUri(코드를 받을 때 쓴 주소)
                    **Returns (쿠키)** ACCESS_TOKEN, REFRESH_TOKEN + newUser(처음 가입이면 true).
                    코드가 틀렸거나 이미 썼으면 AUTH_40006, 허용 안 된 주소면 AUTH_40007,
                    같은 이메일의 이메일 계정이 있으면 AUTH_40902, 카카오 장애면 AUTH_50201
                    """)
    @PostMapping("/kakao")
    public ResponseEntity<BaseResponse<KakaoLoginResponse>> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request) {
        KakaoLoginService.Result result = kakaoLoginService.login(request.code(), request.redirectUri());
        return ResponseEntity.ok()
                .headers(tokenCookies(result.tokens()))
                .body(BaseResponse.success(200, "카카오 로그인에 성공했습니다.", new KakaoLoginResponse(result.newUser())));
    }

    @Operation(
            summary = "[ 사용자 | Refresh 쿠키 | 토큰 재발급 ]",
            description =
                    """
                    **Parameters** 없음 (REFRESH_TOKEN 쿠키를 읽음)
                    **Returns (쿠키)** 새 ACCESS_TOKEN, 새 REFRESH_TOKEN. 이미 쓴·만료·로그아웃한 Refresh 면 AUTH_40103
                    """)
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<Void>> refresh(HttpServletRequest request) {
        TokenResponse tokens = tokenService.reissue(jwtProvider.resolveRefreshToken(request));
        return ResponseEntity.ok()
                .headers(tokenCookies(tokens))
                .body(BaseResponse.success(200, "토큰 재발급에 성공했습니다.", null));
    }

    @Operation(
            summary = "[ 사용자 | Refresh 쿠키 | 로그아웃 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns (쿠키)** 0초짜리 쿠키 두 개 → 브라우저에서 지워짐. 토큰이 없어도 200
                    """)
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(HttpServletRequest request) {
        tokenService.revoke(jwtProvider.resolveRefreshToken(request));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, jwtCookieWriter.expire(TokenType.ACCESS_TOKEN).toString());
        headers.add(HttpHeaders.SET_COOKIE, jwtCookieWriter.expire(TokenType.REFRESH_TOKEN).toString());
        return ResponseEntity.ok()
                .headers(headers)
                .body(BaseResponse.success(200, "로그아웃에 성공했습니다.", null));
    }

    private HttpHeaders tokenCookies(TokenResponse tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, jwtCookieWriter.accessCookie(tokens.accessToken()).toString());
        headers.add(HttpHeaders.SET_COOKIE, jwtCookieWriter.refreshCookie(tokens.refreshToken()).toString());
        return headers;
    }
}
