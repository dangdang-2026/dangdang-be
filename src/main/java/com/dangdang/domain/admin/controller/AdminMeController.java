package com.dangdang.domain.admin.controller;

import com.dangdang.domain.admin.dto.response.AdminMeResponse;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 는 SecurityConfig에서 ADMIN만 들어온다. 그래서 이 API가 200이면 운영자, 403이면 운영자가 아니다
@Tag(name = "Admin · Me", description = "운영자 계정 확인 API")
@RestController
@RequiredArgsConstructor
public class AdminMeController {

    private final UserRepository userRepository;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 내 운영자 계정 ]",
            description =
                    """
                    **Returns** userId, displayName, email. 운영자 화면이 로그인 직후와 새로 열 때 부른다.
                    로그인했지만 ADMIN이 아니면 403(AUTH_40301) → 화면은 「운영진 계정이 아니에요」를 보여 주고 로그아웃한다
                    """)
    @GetMapping("/api/admin/me")
    public ResponseEntity<BaseResponse<AdminMeResponse>> me(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(BaseResponse.success(200, "운영자 계정 조회에 성공했습니다.",
                new AdminMeResponse(user.getId(), user.getDisplayName(), user.getEmail())));
    }
}
