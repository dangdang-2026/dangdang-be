package com.dangdang.domain.auth.controller;

import com.dangdang.domain.auth.dto.request.KakaoLinkRequest;
import com.dangdang.domain.auth.service.KakaoLinkService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// /api/auth/** 는 토큰 없이 열려 있어서, 로그인이 필요한 연결은 내 정보 API(/api/me) 아래에 둔다
@Tag(name = "User", description = "내 정보 API")
@RestController
@RequiredArgsConstructor
public class KakaoLinkController {

    private final KakaoLinkService kakaoLinkService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 카카오 연결 (L-10 · M-02) ]",
            description =
                    """
                    **Parameters** kakaoToken(K-07·K-08에서 받은 것) 또는 code·redirectUri(M-02에서 카카오를 새로 거침) — 하나만
                    **Returns** data 없음. 다음부터 카카오 로그인 = 이 계정. 연결 뒤 /api/me 다시 받기(version이 오름)
                    이미 연결됐으면 AUTH_40904, 이 카카오가 다른 계정에 붙어 있으면 AUTH_40903, 토큰 만료·이미 씀이면 AUTH_40011 (토큰은 연결에 성공할 때만 씀)
                    """)
    @PostMapping("/api/me/kakao")
    public ResponseEntity<BaseResponse<Void>> link(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @Valid @RequestBody KakaoLinkRequest request) {
        kakaoLinkService.link(principal.userId(), request);
        return ResponseEntity.ok(BaseResponse.success(200, "카카오를 연결했습니다.", null));
    }
}
