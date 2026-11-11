package com.dangdang.domain.reminder;

import com.dangdang.domain.criteria.dto.request.CriteriaVersionCreateRequest;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.service.LocalDemoDelay;
import com.dangdang.domain.reminder.service.ReminderService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LocalDemoDelayTest {

    @Autowired ReminderService reminderService;
    @Autowired CriteriaService criteriaService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;   // 진짜 시계 — "지난 시각이면 안 만듦" 검사를 통과하려면 식사 시각이 '지금'이어야 한다

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void local_프로필이면_식후_60분이어도_1분_뒤로_잡힌다() {
        Long userId = userRepository.save(User.ofEmail("jisu@x.com", "지수", "not-used", Instant.now())).getId();
        criteriaService.create(userId, new CriteriaVersionCreateRequest(
                60, List.of(MeasureSlot.LUNCH_AFTER), null, null, null, null));
        Instant startedAt = clock.instant();   // 11/10 고침: 고정 시각(11/09 12:00)이면 이미 지나서 null이 된다
        Long mealId = jdbc.queryForObject("INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, 'LUNCH', ?) RETURNING id",
                Long.class, userId, Timestamp.from(startedAt));

        Reminder reminder = reminderService.scheduleAfterMeal(userId, mealId, MealSlot.LUNCH, startedAt);

        assertThat(reminder.getScheduledAt()).isEqualTo(startedAt.plus(Duration.ofMinutes(1)));
    }

    @Test
    void local과_prod가_같이_켜져도_시연_대기는_안_생긴다() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "prod"))
                .withPropertyValues("reminder.test-delay=1m")
                .withUserConfiguration(LocalDemoDelay.class)
                .run(context -> assertThat(context).doesNotHaveBean(LocalDemoDelay.class));
    }
}
