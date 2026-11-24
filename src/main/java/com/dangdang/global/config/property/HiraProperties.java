package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 심평원(건강보험심사평가원) 공공데이터 API 설정
// serviceKey = 공공데이터포털 일반 인증키 (.env의 HIRA_SERVICE_KEY)
@ConfigurationProperties("hira")
public record HiraProperties(String serviceKey) {

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
