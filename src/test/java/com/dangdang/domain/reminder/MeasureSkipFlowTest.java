package com.dangdang.domain.reminder;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H-05 「이번 끼니는 건너뛸게요」 — 식사에 시각을 남기고 그 식사 알림을 DISMISSED로
class MeasureSkipFlowTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;

    Cookie jisu;
    Long mealId;
    Long reminderId;

    @BeforeEach
    void sentReminderForLunch() throws Exception {
        jisu = login("jisu@x.com");
        subscribe(jisuId, ENDPOINT);
        mealId = jdbc.queryForObject(
                "INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, 'LUNCH', ?) RETURNING id",
                Long.class, jisuId, Timestamp.from(NOW.minus(Duration.ofHours(1))));
        reminderId = reminderAt(jisuId, NOW);
        jdbc.update("UPDATE reminders SET meal_id = ? WHERE id = ?", mealId, reminderId);
        scheduler.run();                         // 13:00 보냄 → SENT
        fakeSender.sent.clear();
    }

    @Test
    void 건너뛰면_식사에_시각이_남고_알림은_DISMISSED_재알림도_안_간다() throws Exception {
        assertThat(statusOf(reminderId)).isEqualTo("SENT");
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measureSkippedAt").value("2026-11-09T04:00:00Z"));
        assertThat(statusOf(reminderId)).isEqualTo("DISMISSED");
        // 식사 상세에도 보이고, 「수정됨」은 안 붙는다
        mvc.perform(get("/api/meals/" + mealId).cookie(jisu))
                .andExpect(jsonPath("$.data.measureSkippedAt").value("2026-11-09T04:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT edited_at IS NULL FROM meals WHERE id = ?", Boolean.class, mealId)).isTrue();

        clock.set(NOW.plus(Duration.ofMinutes(11)));   // 재알림 시각이 지나도
        scheduler.run();
        assertThat(fakeSender.sent).isEmpty();
    }

    @Test
    void 두_번_눌러도_처음_시각_그대로() throws Exception {
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu)).andExpect(status().isOk());
        clock.set(NOW.plus(Duration.ofMinutes(3)));
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measureSkippedAt").value("2026-11-09T04:00:00Z"));
    }

    @Test
    void 건너뛴_알림을_눌러_들어와도_200() throws Exception {
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(post("/api/reminders/" + reminderId + "/open").cookie(jisu)).andExpect(status().isOk());
        assertThat(statusOf(reminderId)).isEqualTo("DISMISSED");
    }

    @Test
    void 건너뛴_뒤_그래도_재면_알림이_DONE으로_붙는다() throws Exception {
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(post("/api/readings").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":120,\"measuredAt\":\"2026-11-09T04:05:00Z\","
                                + "\"readingType\":\"POST_MEAL\",\"mealId\":" + mealId + "}"))
                .andExpect(status().isCreated());
        assertThat(statusOf(reminderId)).isEqualTo("DONE");
    }

    @Test
    void 건너뛴_뒤_잰_혈당을_공복으로_고치면_알림은_다시_DISMISSED() throws Exception {
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu)).andExpect(status().isOk());
        String body = mvc.perform(post("/api/readings").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":120,\"measuredAt\":\"2026-11-09T04:05:00Z\","
                                + "\"readingType\":\"POST_MEAL\",\"mealId\":" + mealId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number readingId = com.jayway.jsonpath.JsonPath.read(body, "$.data.readingId");
        assertThat(statusOf(reminderId)).isEqualTo("DONE");

        // 식사 연결이 끊기면 알림도 풀리는데, 건너뛴 식사라 SENT(→ 다시 울릴 수 있음)가 아니라 DISMISSED로
        mvc.perform(patch("/api/readings/" + readingId).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readingType\":\"FASTING\"}"))
                .andExpect(status().isOk());
        assertThat(statusOf(reminderId)).isEqualTo("DISMISSED");
    }

    @Test
    void 남의_식사나_지운_식사는_404() throws Exception {
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(login("minji@x.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        assertThat(statusOf(reminderId)).isEqualTo("SENT");   // 남이 눌러도 그대로

        jdbc.update("UPDATE meals SET deleted_at = now() WHERE id = ?", mealId);
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu))
                .andExpect(status().isNotFound());
    }

    // 알림이 하나도 없던 식사를 건너뛴 뒤 시작 시각을 고쳐도 새 알림이 안 생긴다 (MealService.update 가드)
    // (알림이 이미 SENT면 rescheduleAfterMeal의 alreadyHandled가 막아서, 가드 자체를 보려면 알림 없는 식사로)
    @Test
    void 건너뛴_뒤_시작_시각을_고쳐도_알림을_다시_안_건다() throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postMealMinutes\":120,\"measureSlots\":[\"LUNCH_AFTER\"],\"effectiveFrom\":\"2026-11-01\"}"))
                .andExpect(status().isCreated());
        Long noReminderMeal = jdbc.queryForObject(
                "INSERT INTO meals (user_id, meal_slot, started_at) VALUES (?, 'LUNCH', ?) RETURNING id",
                Long.class, jisuId, Timestamp.from(NOW.minus(Duration.ofMinutes(30))));
        mvc.perform(post("/api/meals/" + noReminderMeal + "/measure-skip").cookie(jisu)).andExpect(status().isOk());

        mvc.perform(patch("/api/meals/" + noReminderMeal).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\":\"2026-11-09T03:50:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measureSkippedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reminders WHERE meal_id = ?", Integer.class, noReminderMeal))
                .isZero();
    }
}
