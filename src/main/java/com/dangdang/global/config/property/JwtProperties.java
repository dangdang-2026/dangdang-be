package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidityInSeconds,
        long refreshTokenValidityInSeconds,
        boolean secure,
        String sameSite) {

    public JwtProperties {
        if (secret == null || secret.startsWith("${") || secret.length() < 32) {
            throw new IllegalStateException("jwt.secret(JWT_SECRET)이 없거나 32글자보다 짧아요.");
        }
    }
}
