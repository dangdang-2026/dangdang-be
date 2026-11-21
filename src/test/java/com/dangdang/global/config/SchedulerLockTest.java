package com.dangdang.global.config;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

// 스케줄러를 켠 상태(잠금도 같이 켜짐)로 띄워, 알림 한 바퀴가 잠금을 잡고 도는지 본다
// 끝나면 켜진 서버를 닫는다 (안 닫으면 다른 테스트 중에 혼자 돈다)
@SpringBootTest(properties = "reminder.scheduler.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulerLockTest {

    @Autowired ReminderScheduler reminderScheduler;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 잠금_비우기() {
        jdbcTemplate.update("DELETE FROM shedlock");
    }

    @Test
    void 한_바퀴_돌면_잠금_기록이_남는다() {
        reminderScheduler.run();

        assertThat(lockedBy()).isNotBlank();
    }

    @Test
    void 다른_서버가_잠금을_잡고_있으면_이번_바퀴는_건너뛴다() {
        jdbcTemplate.update("""
                INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                VALUES (?, timezone('utc', now()) + interval '5 minutes', timezone('utc', now()), 'other-server')
                """, ReminderScheduler.LOCK_NAME);

        reminderScheduler.run();

        assertThat(lockedBy()).isEqualTo("other-server");   // 잠금을 못 잡았다 = 이번 바퀴는 안 돌았다
    }

    private String lockedBy() {
        return jdbcTemplate.queryForObject(
                "SELECT locked_by FROM shedlock WHERE name = ?", String.class, ReminderScheduler.LOCK_NAME);
    }
}
