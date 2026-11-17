package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.request.WithdrawalRequest;
import com.dangdang.domain.user.service.WithdrawalService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import com.dangdang.global.security.jwt.JwtCookieWriter;
import com.dangdang.global.security.jwt.TokenType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User")
@RestController
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final JwtCookieWriter jwtCookieWriter;

    @Operation(summary = "[ 사용자 | 토큰 O | 회원 탈퇴 (M-08) ]",
            description = """
                    **Parameters** password(이메일 가입자 필수) · keepPosts(기본 false = 내 글·댓글 지움)
                    **Returns** data 없음 · 쿠키 지움. 기록·알림·대화·설정은 바로 지워지고 되돌릴 수 없다
                    비밀번호가 틀리거나 없으면 400 USER_40003
                    """)
    @PostMapping("/api/me/withdrawal")
    public ResponseEntity<BaseResponse<Void>> withdraw(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody WithdrawalRequest request) {
        withdrawalService.withdraw(principal.userId(), request);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, jwtCookieWriter.expire(TokenType.ACCESS_TOKEN).toString());
        headers.add(HttpHeaders.SET_COOKIE, jwtCookieWriter.expire(TokenType.REFRESH_TOKEN).toString());
        return ResponseEntity.ok().headers(headers).body(BaseResponse.success(200, "탈퇴했습니다. 그동안 함께해 주셔서 고마워요.", null));
    }
}
