package com.dangdang.domain.reminder;

import com.dangdang.domain.criteria.dto.request.CriteriaVersionCreateRequest;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.service.ReminderService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReminderServiceTest {
    // 식사 시작 = 지금 (지난 시각이면 알림을 안 만들어서 고정 시각을 못 씀)
    static final Instant NOON = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired ReminderService reminderService;
    @Autowired CriteriaService criteriaService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    Long userId;

    @BeforeEach
    void saveUser() {
        userId = userRepository.save(User.ofEmail("jisu@x.com", "지수", "not-used", Instant.now())).getId();
    }

    @AfterEach
    void clean() { // users를 지우면 meals·reminders·criteria_versions도 같이 지워진다 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
    }

    // Meal 엔티티는 언니 몫이라, 테스트에선 meals 표에 직접 한 줄 넣고 id만 쓴다
    private Long insertMeal(MealSlot slot) {
        return jdbc.queryForObject(
                "INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, ?, ?) RETURNING id",
                Long.class, userId, slot.name(), Timestamp.from(NOON));
    }

    private void saveCriteria(Integer postMealMinutes, List<MeasureSlot> measureSlots) {
        criteriaService.create(userId, new CriteriaVersionCreateRequest(
                postMealMinutes, measureSlots, null, null, null, null));
    }

    private int reminderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM reminders", Integer.class);
    }

    @Test
    void 점심_식사면_식후_시간_뒤로_SCHEDULED_한_줄이_생긴다() {
        saveCriteria(60, List.of(MeasureSlot.LUNCH_AFTER));
        Long mealId = insertMeal(MealSlot.LUNCH);

        Reminder reminder = reminderService.scheduleAfterMeal(userId, mealId, MealSlot.LUNCH, NOON);

        assertThat(reminder.getId()).isNotNull();
        assertThat(reminder.getSlot()).isEqualTo(MeasureSlot.LUNCH_AFTER);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOON.plus(Duration.ofHours(1)));

        var row = jdbc.queryForMap("SELECT user_id, meal_id, slot, status FROM reminders WHERE id = ?", reminder.getId());
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("meal_id")).isEqualTo(mealId);
        assertThat(row.get("slot")).isEqualTo("LUNCH_AFTER");
        assertThat(row.get("status")).isEqualTo("SCHEDULED");
    }

    @Test
    void 직접_입력한_90분이면_90분_뒤로_잡힌다() {
        saveCriteria(90, List.of(MeasureSlot.DINNER_AFTER));

        Reminder reminder = reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.DINNER), MealSlot.DINNER, NOON);

        assertThat(reminder.getSlot()).isEqualTo(MeasureSlot.DINNER_AFTER);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOON.plus(Duration.ofMinutes(90)));
    }

    @Test
    void 식후_시간이_아직_몰라요면_null() {
        saveCriteria(null, List.of(MeasureSlot.LUNCH_AFTER));

        assertThat(reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.LUNCH), MealSlot.LUNCH, NOON)).isNull();
        assertThat(reminderCount()).isZero();
    }

    @Test
    void 기준이_하나도_없으면_null이고_빈_기준도_안_만든다() {
        assertThat(reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.LUNCH), MealSlot.LUNCH, NOON)).isNull();
        assertThat(reminderCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM criteria_versions", Integer.class)).isZero();
    }

    @Test
    void 병원에서_재라고_한_때가_아니면_null() {
        saveCriteria(60, List.of(MeasureSlot.FASTING, MeasureSlot.DINNER_AFTER));

        assertThat(reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.LUNCH), MealSlot.LUNCH, NOON)).isNull();
        assertThat(reminderCount()).isZero();
    }

    @Test
    void 재는_때를_아직_안_정했으면_null() {
        saveCriteria(60, null);

        assertThat(reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.LUNCH), MealSlot.LUNCH, NOON)).isNull();
        assertThat(reminderCount()).isZero();
    }

    @Test
    void 예약_시각이_이미_지났으면_null() {   // 어제 점심을 오늘 적은 경우 등
        saveCriteria(60, List.of(MeasureSlot.LUNCH_AFTER));
        Instant yesterdayNoon = NOON.minus(Duration.ofDays(1));

        assertThat(reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.LUNCH), MealSlot.LUNCH, yesterdayNoon)).isNull();
        assertThat(reminderCount()).isZero();
    }

    @Test
    void 간식이면_null() {
        saveCriteria(60, List.of(MeasureSlot.LUNCH_AFTER));

        assertThat(reminderService.scheduleAfterMeal(userId, insertMeal(MealSlot.SNACK), MealSlot.SNACK, NOON)).isNull();
        assertThat(reminderCount()).isZero();
    }
}
