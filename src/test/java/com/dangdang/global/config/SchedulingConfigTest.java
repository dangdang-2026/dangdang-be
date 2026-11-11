package com.dangdang.global.config;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// 스케줄러를 켠 상태로 서버를 띄워, 30초마다 도는 일이 진짜로 등록됐는지 본다
// 이 테스트가 끝나면 켜진 서버를 닫는다 (안 닫으면 다른 테스트 중에 혼자 돈다)
@SpringBootTest(properties = "reminder.scheduler.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingConfigTest {

    @Autowired ApplicationContext context;

    @Test
    void 스케줄러가_30초_간격으로_등록된다() {
        assertThat(context.getBean(SchedulingConfig.class)).isNotNull();

        var tasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .filter(task -> task.toString().contains(ReminderScheduler.class.getName()))
                .toList();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTask()).isInstanceOf(FixedDelayTask.class);
        assertThat(((FixedDelayTask) tasks.get(0).getTask()).getIntervalDuration()).isEqualTo(Duration.ofSeconds(30));
    }
}
