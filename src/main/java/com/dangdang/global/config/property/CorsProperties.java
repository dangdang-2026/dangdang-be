package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// allowedOrigins: 앱(사용자 화면) 주소
// adminAllowedOrigins: 운영자 화면 주소 — 운영자 화면은 배포하지 않고 운영진 PC(localhost)에서만 띄운다
@ConfigurationProperties("cors")
public record CorsProperties(List<String> allowedOrigins, List<String> adminAllowedOrigins) {
    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
        adminAllowedOrigins = adminAllowedOrigins == null ? List.of() : adminAllowedOrigins;
    }
}
