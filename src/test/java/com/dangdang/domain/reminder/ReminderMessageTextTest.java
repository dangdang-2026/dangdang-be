package com.dangdang.domain.reminder;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 피그마 와프 E-05(푸시)·H-15(알림함) 문구 그대로 나가는지 (NOW = 한국 13:00)
class ReminderMessageTextTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;

    @BeforeEach
    void showContent() {
        subscribe(jisuId, ENDPOINT);
    }

    // 식사 한 줄을 넣고, 그 식사에서 만든 알림을 NOW에 예약한다
    private Long mealReminder(MeasureSlot slot, String mealSlot, Duration sinceMeal) {
        Instant startedAt = NOW.minus(sinceMeal);
        Long mealId = jdbc.queryForObject(
                "INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, ?, ?) RETURNING id",
                Long.class, jisuId, mealSlot, Timestamp.from(startedAt));
        User user = userRepository.findById(jisuId).orElseThrow();
        return reminderRepository.save(Reminder.scheduled(user, mealId, slot, NOW, startedAt)).getId();
    }

    private void hideContent(boolean hide) {
        jdbc.update("INSERT INTO notification_preferences (user_id, hide_preview) VALUES (?, ?)", jisuId, hide);
    }

    private Map<String, Object> lastInbox() {
        return jdbc.queryForMap("SELECT title, body FROM notifications ORDER BY id DESC LIMIT 1");
    }

    @Test
    void 식후_1시간_알림은_피그마_문구로_간다() {
        hideContent(false);
        mealReminder(MeasureSlot.LUNCH_AFTER, "LUNCH", Duration.ofHours(1));   // 12:00 식사 시작

        scheduler.run();

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("점심 식후 1시간 혈당 잴 시간이에요");
        assertThat(lastInbox().get("title")).isEqualTo("점심 식후 1시간, 잴 시간이에요");
        assertThat(lastInbox().get("body")).isEqualTo("12:00에 식사를 시작했어요");
    }

    @Test
    void 식후_1시간_30분이면_분까지_적는다() {
        hideContent(false);
        mealReminder(MeasureSlot.DINNER_AFTER, "DINNER", Duration.ofMinutes(90));   // 11:30 식사 시작

        scheduler.run();

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("저녁 식후 1시간 30분 혈당 잴 시간이에요");
        assertThat(lastInbox().get("title")).isEqualTo("저녁 식후 1시간 30분, 잴 시간이에요");
        assertThat(lastInbox().get("body")).isEqualTo("11:30에 식사를 시작했어요");
    }

    @Test
    void 식사가_없는_공복_알림은_내용_없이_간다() {
        hideContent(false);
        User user = userRepository.findById(jisuId).orElseThrow();
        reminderRepository.save(Reminder.scheduled(user, null, MeasureSlot.FASTING, NOW, NOW));

        scheduler.run();

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("공복 혈당을 잴 시간이에요");
        assertThat(lastInbox().get("title")).isEqualTo("공복 혈당을 잴 시간이에요");
        assertThat(lastInbox().get("body")).isNull();
    }

    @Test
    void 잠금화면_가리기가_켜져_있으면_확인할_일이_있어요만_보인다() {
        mealReminder(MeasureSlot.LUNCH_AFTER, "LUNCH", Duration.ofHours(1));   // 설정 줄 없음 = 가리기 켬(기본값)

        scheduler.run();

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("확인할 일이 있어요");
        assertThat(lastInbox().get("title")).isEqualTo("점심 식후 1시간, 잴 시간이에요");   // 알림함은 원래 문구
    }

    @Test
    void 재알림도_알림함_내용에_식사_시각이_남는다() {
        mealReminder(MeasureSlot.LUNCH_AFTER, "LUNCH", Duration.ofHours(1));
        scheduler.run();

        clock.set(NOW.plus(Duration.ofMinutes(10)));
        scheduler.run();

        assertThat(lastInbox().get("title")).isEqualTo("아직 혈당을 안 쟀다면 지금 재 주세요");
        assertThat(lastInbox().get("body")).isEqualTo("12:00에 식사를 시작했어요");
    }
}
