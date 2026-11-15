package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

// AI 서버(dangdang-ai) 주소와 내부 토큰. 브라우저는 AI 서버를 직접 부르지 않는다
@ConfigurationProperties("ai")
public record AiProperties(String baseUrl, String internalToken) {
}
