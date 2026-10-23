package com.dangdang.domain.user.controller;

import com.dangdang.domain.user.dto.response.MeResponse;
import com.dangdang.domain.user.service.UserService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="User", description = "내 정보 API")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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

}

