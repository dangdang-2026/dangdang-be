package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("kakao")
public record KakaoProperties(String clientId, String clientSecret, List<String> redirectUris) {

    public boolean isAllowedRedirect(String redirectUri) {
        return redirectUris != null && redirectUris.contains(redirectUri);
    }
}
