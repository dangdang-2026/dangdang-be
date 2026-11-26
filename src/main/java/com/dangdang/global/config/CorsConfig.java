package com.dangdang.global.config;

import com.dangdang.global.config.property.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {
    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 운영자 API는 운영자 화면 주소에서만 — 화면을 복사해 다른 곳에 올려도 브라우저가 막는다.
        // (진짜 잠금은 ADMIN 권한 확인이고, 이건 두 번째 잠금이다)
        CorsConfiguration admin = base(corsProperties.adminAllowedOrigins());

        // 나머지 API는 앱 주소 + 운영자 화면 주소 (운영자도 /api/auth/login 으로 로그인한다)
        List<String> all = new ArrayList<>(corsProperties.allowedOrigins());
        all.addAll(corsProperties.adminAllowedOrigins());
        CorsConfiguration app = base(all);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 먼저 등록한 규칙이 먼저 맞춰진다 — /api/admin/** 를 /api/** 보다 앞에 둔다
        source.registerCorsConfiguration("/api/admin/**", admin);
        source.registerCorsConfiguration("/api/**", app);
        return source;
    }

    private CorsConfiguration base(List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins); // 허락한 프론트 주소만
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 쿠키(로그인 토큰)도 같이 보내게
        config.setMaxAge(3600L); // 사전 확인(OPTIONS) 결과를 1시간 기억
        return config;
    }
}
