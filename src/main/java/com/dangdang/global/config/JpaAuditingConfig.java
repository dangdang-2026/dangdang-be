package com.dangdang.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

// createdAt·updatedAt 자동 기록. 시각은 앱의 Clock에서 가져옴
// (식사 항목이 바뀔 때 식사 updatedAt을 직접 올리는 곳도 Clock을 써서, 같은 칸에 시간 출처가 하나가 되게)
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
