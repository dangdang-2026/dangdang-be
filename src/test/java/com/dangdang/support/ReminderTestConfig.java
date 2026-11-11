package com.dangdang.support;

import com.dangdang.global.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

// 알림 테스트 전부가 같이 쓰는 가짜 — 빈 이름이 같으면 충돌하니 clock이 아니라 testClock
@TestConfiguration
public class ReminderTestConfig {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-11-09T04:00:00Z"), ClockConfig.KST);
    }

    @Bean
    @Primary
    public FakeWebPushSender fakeWebPushSender() {
        return new FakeWebPushSender();
    }
}
