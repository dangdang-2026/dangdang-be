package com.dangdang.domain.user.controller;

import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.user.dto.request.PasswordChangeRequest;
import com.dangdang.domain.user.dto.response.MeResponse;
import com.dangdang.domain.user.service.PasswordChangeService;
import com.dangdang.domain.user.service.UserService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import com.dangdang.global.security.jwt.JwtCookieWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name="User", description = "내 정보 API")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordChangeService passwordChangeService;
    private final JwtCookieWriter jwtCookieWriter;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 내 정보 ]",
            description =
                    """
                    **Parameters** 없음 (ACCESS_TOKEN 쿠키 또는 Authorization: Bearer)
                    **Returns** userId, email, displayName, authProvider. 토큰 없으면 AUTH_40101, 만료면 AUTH_40102
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<MeResponse>> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(
                BaseResponse.success(200, "내 정보 조회에 성공했습니다.", userService.getMe(principal.userId())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 비밀번호 바꾸기 ]",
            description =
                    """
                    **Parameters** currentPassword(지금 비밀번호), newPassword(8자 이상, 영문·숫자)
                    **Returns (쿠키)** 새 ACCESS_TOKEN, REFRESH_TOKEN. 다른 기기는 로그아웃.
                    카카오 계정 USER_40002, 지금 비밀번호 틀림 USER_40003, 이전과 같음 AUTH_40009
                    """)
    @PatchMapping("/password")
    public ResponseEntity<BaseResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PasswordChangeRequest request) {
        TokenResponse tokens = passwordChangeService.change(principal.userId(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookieWriter.accessCookie(tokens.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, jwtCookieWriter.refreshCookie(tokens.refreshToken()).toString())
                .body(BaseResponse.success(200, "비밀번호를 바꿨어요.", null));
    }

}

