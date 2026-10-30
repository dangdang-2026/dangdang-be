package com.dangdang.domain.auth.infra.kakao;

//인가 코드 -> 카카오 사용자
public interface KakaoClient {

    KakaoUser fetchUser(String code, String redirectUri);
}
