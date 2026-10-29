package com.dangdang.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    // 한국 시간대 — 다른 코드에서도 ClockConfig.KST 로 꺼내 쓴다
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);   // 한국 시간대로 도는 진짜 시계
    }
}