package com.dangdang.domain.auth.infra.kakao;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.global.config.property.KakaoProperties;
import com.dangdang.global.exception.CustomException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class KakaoApiClient implements KakaoClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoApiClient(KakaoProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000); //연결 3초
        factory.setReadTimeout(10_000);     //응답 10초
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public KakaoUser fetchUser(String code, String redirectUri) {
        try {
            String kakaoAccessToken = requestToken(code, redirectUri);
            return requestUser(kakaoAccessToken);
        } catch (HttpClientErrorException e) {        //4xx:코드가 틀렸거나 이미 썼거나 주소가 다른
            log.warn("[Kakao] 거절 - {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(AuthErrorCode.KAKAO_LOGIN_FAILED);
        } catch (RestClientException e) {            //5xx-시간 초과 : 카카오 쪽 문제
            log.error("[Kakao] 연결 실패", e);
            throw new CustomException(AuthErrorCode.KAKAO_UNAVAILABLE);
        }
    }
    // ① 인가 코드 → 카카오 액세스 토큰 (form 형식으로 보내기)
    private String requestToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }
        TokenBody body =
                restClient.post()
                        .uri(TOKEN_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenBody.class);
        return body.accessToken();
    }

    // ② 카카오 액세스 토큰 → 회원번호·이메일·닉네임
    private KakaoUser requestUser(String kakaoAccessToken) {
        UserBody body =
                restClient.get()
                        .uri(USER_URL)
                        .header("Authorization", "Bearer " + kakaoAccessToken)
                        .retrieve()
                        .body(UserBody.class);
        Account account = body.kakaoAccount();
        String email = null;
        String nickname = null;
        if (account != null) {
            // 인증된 이메일만 쓴다.
            if (Boolean.TRUE.equals(account.isEmailValid()) && Boolean.TRUE.equals(account.isEmailVerified())) {
                email = account.email();
            }
            if (account.profile() != null) {
                nickname = account.profile().nickname();
            }
        }
        return new KakaoUser(String.valueOf(body.id()), email, nickname);
    }

    // 카카오 응답 모양 (필요한 칸만)
    record TokenBody(@JsonProperty("access_token") String accessToken) {}

    record UserBody(Long id, @JsonProperty("kakao_account") Account kakaoAccount) {}

    record Account(
            String email,
            @JsonProperty("is_email_valid") Boolean isEmailValid,
            @JsonProperty("is_email_verified") Boolean isEmailVerified,
            Profile profile) {}

    record Profile(String nickname) {}
}



