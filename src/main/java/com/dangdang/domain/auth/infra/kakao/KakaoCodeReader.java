package com.dangdang.domain.auth.infra.kakao;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.config.property.KakaoProperties;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 카카오 인가 코드 → 카카오 사용자. 우리가 콘솔에 등록한 주소인지 먼저 본다
// 카카오 로그인(K-07·K-08) · 카카오 연결(M-02) · 탈퇴 다시 확인(M-08) 세 곳이 같이 쓴다
@Component
@RequiredArgsConstructor
public class KakaoCodeReader {

    private final KakaoClient kakaoClient;
    private final KakaoProperties kakaoProperties;

    public KakaoUser read(String code, String redirectUri) {
        if (!kakaoProperties.isAllowedRedirect(redirectUri)) {
            throw new CustomException(AuthErrorCode.KAKAO_REDIRECT_NOT_ALLOWED);   // AUTH_40007
        }
        return kakaoClient.fetchUser(code, redirectUri);   // 틀린·쓴 코드 AUTH_40006 · 카카오 장애 AUTH_50201
    }
}
