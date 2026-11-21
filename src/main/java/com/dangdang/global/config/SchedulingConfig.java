package com.dangdang.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

// @Scheduled 를 켠다 — 이 파일이 없으면 에러 없이 조용히 안 돈다
// 테스트에서만 reminder.scheduler.enabled=false 로 끈다 (값이 없으면 켜진 것)
// 잠금(ShedLock): 서버가 여러 대여도 같은 일은 한 대만 돌린다. 잠금은 DB shedlock 테이블에 남는다
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@ConditionalOnProperty(name = "reminder.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    // 잠금 시각은 DB 시계로 잰다 (서버마다 시계가 조금씩 달라도 잠금이 어긋나지 않게)
    // 알림 보낼 때가 됐는지 같은 업무 시각은 지금처럼 앱 Clock 을 쓴다 (테스트에서 MutableClock 으로 움직이게)
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
