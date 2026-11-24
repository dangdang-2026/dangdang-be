package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 심평원(건강보험심사평가원) 공공데이터 API 설정
// serviceKey = 공공데이터포털 일반 인증키 (.env의 HIRA_SERVICE_KEY)
// syncEnabled = 병원 목록을 DB에 받아 두는 일(처음 한 번 + 매주)을 켤지. 운영·개발 서버만 true, 로컬·테스트는 false
@ConfigurationProperties("hira")
public record HiraProperties(String serviceKey, boolean syncEnabled) {

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
