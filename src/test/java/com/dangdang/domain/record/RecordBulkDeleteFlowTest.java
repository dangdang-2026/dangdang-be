package com.dangdang.domain.record;

import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 기간 골라 기록 지우기 (M-07)
class RecordBulkDeleteFlowTest extends ReminderTestBase {

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    @Test
    void 기간_안의_혈당_식사_인슐린만_지우고_개수를_준다() throws Exception {
        reading(jisu, "2026-11-01T07:00:00+09:00");
        reading(jisu, "2026-11-03T23:30:00+09:00");   // 한국 11/03 (UTC로는 11/03 14:30)
        reading(jisu, "2026-11-04T00:10:00+09:00");   // 한국 11/04 → 기간 밖 (UTC로는 11/03이지만 안 지워져야 함)
        meal(jisu, "2026-11-02T12:00:00+09:00");
        insulin(jisu, "2026-11-02T21:00:00+09:00");
        insulin(jisu, "2026-11-05T21:00:00+09:00");   // 기간 밖
        reading(minji, "2026-11-02T07:00:00+09:00");  // 남의 기록

        mvc.perform(delete("/api/records").param("from", "2026-11-01").param("to", "2026-11-03").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-01"))
                .andExpect(jsonPath("$.data.to").value("2026-11-03"))
                .andExpect(jsonPath("$.data.deletedReadings").value(2))
                .andExpect(jsonPath("$.data.deletedMeals").value(1))
                .andExpect(jsonPath("$.data.deletedInsulinLogs").value(1));

        assertThat(alive("readings", jisuId)).isEqualTo(1);
        assertThat(alive("meals", jisuId)).isZero();
        assertThat(alive("insulin_logs", jisuId)).isEqualTo(1);
        assertThat(alive("readings", minjiId)).isEqualTo(1);
    }

    @Test
    void 이미_지운_기록은_다시_세지_않는다() throws Exception {
        reading(jisu, "2026-11-01T07:00:00+09:00");
        mvc.perform(delete("/api/records").param("from", "2026-11-01").param("to", "2026-11-01").cookie(jisu))
                .andExpect(jsonPath("$.data.deletedReadings").value(1));
        mvc.perform(delete("/api/records").param("from", "2026-11-01").param("to", "2026-11-01").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedReadings").value(0));
    }

    @Test
    void 지운_식사의_아직_안_보낸_식후_알림은_취소된다() throws Exception {
        send(post("/api/criteria-versions"), jisu,
                "{\"postMealMinutes\":120,\"measureSlots\":[\"LUNCH_AFTER\"],\"effectiveFrom\":\"2026-11-01\"}")
                .andExpect(status().isCreated());
        long mealId = meal(jisu, NOW.minus(Duration.ofMinutes(10)).toString());
        assertThat(jdbc.queryForObject("SELECT status FROM reminders WHERE meal_id = ?", String.class, mealId))
                .isEqualTo("SCHEDULED");

        mvc.perform(delete("/api/records").param("from", "2026-11-09").param("to", "2026-11-09").cookie(jisu))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM reminders WHERE meal_id = ?", String.class, mealId))
                .isEqualTo("CANCELED");
    }

    // to 날 23:30 저녁 + 다음 날 01:30 식후 혈당 → 식사와 같이 혈당도 지워서 지운 식사를 가리키는 혈당이 안 남는다
    @Test
    void 기간_안_식사에_붙은_식후_혈당은_기간_밖이어도_같이_지운다() throws Exception {
        long mealId = meal(jisu, "2026-11-03T23:30:00+09:00");
        long reminderId = sentReminderFor(mealId);
        long readingId = postMeal(jisu, mealId, "2026-11-04T01:30:00+09:00");
        assertThat(statusOf(reminderId)).isEqualTo("DONE");

        mvc.perform(delete("/api/records").param("from", "2026-11-01").param("to", "2026-11-03").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedMeals").value(1))
                .andExpect(jsonPath("$.data.deletedReadings").value(1));

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM readings WHERE id = ?", Boolean.class, readingId))
                .isTrue();
        assertThat(statusOf(reminderId)).isEqualTo("CANCELED");   // 지운 식사의 알림은 되살리지 않고 취소
    }

    // from 전날 23:50 식사 + from 날 00:30 식후 혈당 → 혈당만 지우고, 그 혈당이 끝낸 알림은 풀어서 「쟀음」으로 남지 않게
    @Test
    void 기간_밖_식사에_붙은_혈당만_지우면_알림이_풀린다() throws Exception {
        long mealId = meal(jisu, "2026-11-02T23:50:00+09:00");
        long reminderId = sentReminderFor(mealId);
        postMeal(jisu, mealId, "2026-11-03T00:30:00+09:00");
        assertThat(statusOf(reminderId)).isEqualTo("DONE");

        mvc.perform(delete("/api/records").param("from", "2026-11-03").param("to", "2026-11-03").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedMeals").value(0))
                .andExpect(jsonPath("$.data.deletedReadings").value(1));

        assertThat(alive("meals", jisuId)).isEqualTo(1);
        assertThat(statusOf(reminderId)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT reading_id IS NULL FROM reminders WHERE id = ?", Boolean.class, reminderId))
                .isTrue();
    }

    @Test
    void 기간이_거꾸로거나_366일을_넘거나_날짜가_틀리면_400() throws Exception {
        mvc.perform(delete("/api/records").param("from", "2026-11-05").param("to", "2026-11-01").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(delete("/api/records").param("from", "2025-11-01").param("to", "2026-11-02").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(delete("/api/records").param("from", "2026-11-1").param("to", "2026-11-02").cookie(jisu))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/records").param("from", "2025-11-01").param("to", "2026-11-01").cookie(jisu))
                .andExpect(status().isOk());   // 양 끝 포함 366일은 됨
    }

    private void reading(Cookie who, String measuredAt) throws Exception {
        send(post("/api/readings"), who, "{\"value\":110,\"measuredAt\":\"" + measuredAt + "\",\"readingType\":\"OTHER\"}")
                .andExpect(status().isCreated());
    }

    private long meal(Cookie who, String startedAt) throws Exception {
        return id(send(post("/api/meals"), who, "{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + startedAt + "\"}")
                .andExpect(status().isCreated()), "$.data.mealId");
    }

    private long postMeal(Cookie who, long mealId, String measuredAt) throws Exception {
        return id(send(post("/api/readings"), who, "{\"value\":130,\"measuredAt\":\"" + measuredAt
                        + "\",\"readingType\":\"POST_MEAL\",\"mealId\":" + mealId + "}")
                .andExpect(status().isCreated()), "$.data.readingId");
    }

    // 이 식사로 이미 보낸 식후 알림 하나
    private long sentReminderFor(long mealId) {
        long reminderId = reminderAt(jisuId, NOW.minus(Duration.ofDays(6)));
        jdbc.update("UPDATE reminders SET meal_id = ?, status = 'SENT', sent_at = scheduled_at WHERE id = ?",
                mealId, reminderId);
        return reminderId;
    }

    private void insulin(Cookie who, String takenAt) throws Exception {
        send(post("/api/insulin-logs"), who, "{\"insulinType\":\"BEDTIME\",\"units\":6,\"takenAt\":\"" + takenAt + "\"}")
                .andExpect(status().isCreated());
    }

    private int alive(String table, Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ? AND deleted_at IS NULL",
                Integer.class, userId);
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Cookie who, String body) throws Exception {
        return mvc.perform(req.cookie(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
