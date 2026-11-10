package com.dangdang.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 를 켠다 — 이 파일이 없으면 에러 없이 조용히 안 돈다
// 테스트에서만 reminder.scheduler.enabled=false 로 끈다 (값이 없으면 켜진 것)
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "reminder.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
