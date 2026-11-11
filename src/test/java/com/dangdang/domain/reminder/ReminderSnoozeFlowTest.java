package com.dangdang.domain.reminder;

import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.reminder.dto.response.NextReminderResponse;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.domain.reminder.service.ReminderQueryService;
import com.dangdang.domain.reminder.service.ReminderService;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderSnoozeFlowTest extends ReminderTestBase {

    static final Duration TEN = Duration.ofMinutes(10);

    @Autowired ReminderScheduler scheduler;
    @Autowired ReminderQueryService reminderQueryService;
    @Autowired ReminderService reminderService;

    Cookie jisu;
    Long id;

    @BeforeEach
    void sendOnce() throws Exception {
        jisu = login("jisu@x.com");
        subscribe(jisuId, ENDPOINT);
        id = reminderAt(jisuId, NOW);
        scheduler.run();
        fakeSender.sent.clear();
    }

    private ResultActions snooze(Cookie who, Long reminderId) throws Exception {
        return mvc.perform(post("/api/reminders/" + reminderId + "/snooze").cookie(who));
    }

    // ★ 11/10 — 언니 #37 혈당 저장 길을 그대로 탄다: 식사 한 줄을 넣고 이 알림에 붙인 뒤 식사 id를 돌려준다
    private Long attachMeal() {
        Long mealId = jdbc.queryForObject(
                "INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, 'LUNCH', ?) RETURNING id",
                Long.class, jisuId, Timestamp.from(NOW.minus(Duration.ofHours(1))));
        jdbc.update("UPDATE reminders SET meal_id = ? WHERE id = ?", mealId, id);
        return mealId;
    }

    // 언니 POST /api/readings → ReadingService → reminderService.completeForMeal(mealId, readingId)
    private void saveReading(Long mealId) throws Exception {
        mvc.perform(post("/api/readings").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":120,\"measuredAt\":\"2026-11-09T04:05:00Z\","
                                + "\"readingType\":\"POST_MEAL\",\"mealId\":" + mealId + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 보낸_알림을_미루면_다시_예약되고_원래_예정_시각은_그대로() throws Exception {
        snooze(jisu, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledAt").value("2026-11-09T04:00:00Z"))
                .andExpect(jsonPath("$.data.snoozedUntil").value("2026-11-09T04:10:00Z"));

        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT scheduled_at FROM reminders WHERE id = ?", Timestamp.class, id).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void 열어_본_알림도_미룰_수_있다() throws Exception {
        setStatus(id, "OPENED");

        snooze(jisu, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void 미룰_수_없는_상태는_409() throws Exception {
        for (String state : List.of("SCHEDULED", "DONE", "SKIPPED", "CANCELED", "PAUSED")) {
            setStatus(id, state);
            snooze(jisu, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("REMINDER_40902"));
            assertThat(statusOf(id)).isEqualTo(state);
        }
    }

    @Test
    void 남의_알림이나_없는_번호는_404() throws Exception {
        snooze(login("minji@x.com"), id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_40401"));
        snooze(jisu, 999999L).andExpect(status().isNotFound());
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(post("/api/reminders/" + id + "/snooze")).andExpect(status().isUnauthorized());
    }

    @Test
    void 미룬_알림은_정확히_10분_뒤에_다시_간다() throws Exception {
        snooze(jisu, id);

        clock.set(NOW.plus(TEN).minusSeconds(1));
        scheduler.run();
        assertThat(fakeSender.sent).isEmpty();
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");

        clock.set(NOW.plus(TEN));
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);
        assertThat(statusOf(id)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT sent_at FROM reminders WHERE id = ?", Timestamp.class, id).toInstant())
                .isEqualTo(NOW.plus(TEN));
        assertThat(inboxCount()).isEqualTo(2);
    }

    @Test
    void 직접_미룬_알림에는_자동_재알림이_더_붙지_않는다() throws Exception {
        snooze(jisu, id);
        clock.set(NOW.plus(TEN));
        scheduler.run();
        fakeSender.sent.clear();

        clock.set(NOW.plus(TEN).plus(TEN));
        scheduler.run();

        assertThat(fakeSender.sent).isEmpty();
    }

    @Test
    void 미룬_뒤_다시_받아_열어도_처음_연_시각이_남는다() throws Exception {
        mvc.perform(post("/api/reminders/" + id + "/open").cookie(jisu));
        snooze(jisu, id);
        clock.set(NOW.plus(TEN));
        scheduler.run();

        clock.set(NOW.plus(TEN).plusSeconds(30));
        mvc.perform(post("/api/reminders/" + id + "/open").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPENED"))
                .andExpect(jsonPath("$.data.openedAt").value("2026-11-09T04:00:00Z"));
    }

    @Test
    void 미룬_알림의_findNext는_실제로_울릴_시각을_준다() throws Exception {
        snooze(jisu, id);

        NextReminderResponse next = reminderQueryService.findNext(jisuId);

        assertThat(next.reminderId()).isEqualTo(id);
        assertThat(next.scheduledAt()).isEqualTo(NOW.plus(TEN));
    }

    @Test
    void 미룬_알림도_혈당을_저장하면_DONE이_되고_원래_예정_시각은_그대로() throws Exception {
        Long mealId = attachMeal();
        snooze(jisu, id);

        saveReading(mealId);

        assertThat(statusOf(id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT reading_id FROM reminders WHERE id = ?", Long.class, id)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT scheduled_at FROM reminders WHERE id = ?", Timestamp.class, id).toInstant())
                .isEqualTo(NOW);                 // 늦게 잼은 이 원래 예정 시각으로 계산한다

        clock.set(NOW.plus(TEN));                // 미뤘던 10분이 지나도
        scheduler.run();
        assertThat(fakeSender.sent).isEmpty();   // 다시 울리지 않는다
    }

    @Test
    void 재알림까지_간_알림도_혈당을_저장하면_DONE이_되고_더_안_울린다() throws Exception {
        Long mealId = attachMeal();
        clock.set(NOW.plus(TEN));
        scheduler.run();                         // 10분 뒤 재알림 (상태는 SENT 그대로)
        assertThat(fakeSender.sent).hasSize(1);

        saveReading(mealId);

        assertThat(statusOf(id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT renotified FROM reminders WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void 미룬_알림은_다시_보내기_전에_측정_알림을_끄면_SKIPPED() throws Exception {
        snooze(jisu, id);
        jdbc.update("INSERT INTO notification_preferences (user_id, measurement_enabled) VALUES (?, false)", jisuId);

        clock.set(NOW.plus(TEN));
        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SKIPPED");
        assertThat(fakeSender.sent).isEmpty();
    }
    //미룬 알림(SCHEDULED지만 한 번 보냄)이 있는 식사를 고쳐도 취소·새 예약하지 않는다
    @Test
    void 미룬_뒤_식사_시각을_고쳐도_미룬_알림은_그대로고_새_알림은_안_생긴다() throws Exception {
        Long mealId = attachMeal();
        snooze(jisu, id);

        Reminder created = reminderService.rescheduleAfterMeal(
                jisuId, mealId, MealSlot.LUNCH, NOW.minus(Duration.ofMinutes(50)));   // 식사 시각을 10분 늦게 고침

        assertThat(created).isNull();                                   // 새 알림을 만들지 않았다
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");                // 미룬 알림은 취소되지 않았다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reminders WHERE meal_id = ?", Integer.class, mealId))
                .isEqualTo(1);

        clock.set(NOW.plus(TEN));                                       // 미룬 대로 10분 뒤
        scheduler.run();
        assertThat(fakeSender.sent).hasSize(1);                         // 딱 한 번 다시 간다
    }
}

