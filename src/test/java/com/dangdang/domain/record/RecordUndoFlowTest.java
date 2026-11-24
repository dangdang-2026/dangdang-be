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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 지운 혈당·식사 되돌리기 (D-08). 시계(MutableClock)를 움직여 5분을 잰다
class RecordUndoFlowTest extends ReminderTestBase {

    static final String READING = "{\"value\":92,\"measuredAt\":\"2026-11-09T07:00:00+09:00\",\"readingType\":\"FASTING\"}";
    static final String LUNCH_CRITERIA =
            "{\"postMealMinutes\":120,\"measureSlots\":[\"LUNCH_AFTER\"],\"effectiveFrom\":\"2026-11-01\"}";

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    @Test
    void 지운_지_5분_안이면_혈당이_다시_보이고_수정됨은_안_붙는다() throws Exception {
        long readingId = deletedReading();
        clock.set(NOW.plus(Duration.ofMinutes(5)));   // 딱 5분까지는 됨

        mvc.perform(post("/api/readings/" + readingId + "/restore").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readingId").value(readingId))
                .andExpect(jsonPath("$.data.value").value(92))
                .andExpect(jsonPath("$.data.editedAt").value((Object) null));
        mvc.perform(get("/api/readings/" + readingId).cookie(jisu)).andExpect(status().isOk());
    }

    @Test
    void 오분이_지나면_409_READING_40902() throws Exception {
        long readingId = deletedReading();
        clock.set(NOW.plus(Duration.ofMinutes(5)).plusSeconds(1));

        mvc.perform(post("/api/readings/" + readingId + "/restore").cookie(jisu))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("READING_40902"));
        mvc.perform(get("/api/readings/" + readingId).cookie(jisu)).andExpect(status().isNotFound());
    }

    @Test
    void 두_번_누르면_두_번째는_409_READING_40901() throws Exception {
        long readingId = deletedReading();
        mvc.perform(post("/api/readings/" + readingId + "/restore").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(post("/api/readings/" + readingId + "/restore").cookie(jisu))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("READING_40901"));
    }

    @Test
    void 동시에_두_번_되돌리면_하나만_200_나머지는_409() throws Exception {
        long readingId = deletedReading();
        assertThat(concurrently("/api/readings/" + readingId + "/restore")).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM readings WHERE id = ?", Boolean.class, readingId))
                .isTrue();
    }

    @Test
    void 남의_기록이나_없는_번호는_404() throws Exception {
        long readingId = deletedReading();
        mvc.perform(post("/api/readings/" + readingId + "/restore").cookie(minji))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("READING_40401"));
        mvc.perform(post("/api/readings/999999/restore").cookie(jisu)).andExpect(status().isNotFound());
        // 남이 눌러도 지운 채 그대로
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM readings WHERE id = ?", Boolean.class, readingId))
                .isFalse();
    }

    @Test
    void 건강정보_동의를_철회했으면_되돌리기도_403() throws Exception {
        long readingId = deletedReading();
        jdbc.update("INSERT INTO consent_events (user_id, consent_type, document_version, granted, occurred_at)"
                + " SELECT user_id, consent_type, document_version, false, now() FROM consent_events WHERE user_id = ?", jisuId);
        mvc.perform(post("/api/readings/" + readingId + "/restore").cookie(jisu))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40302"));
    }

    @Test
    void 식사를_되돌리면_취소됐던_식후_알림이_다시_예약된다() throws Exception {
        send(post("/api/criteria-versions"), jisu, LUNCH_CRITERIA).andExpect(status().isCreated());
        long mealId = lunchTenMinutesAgo();
        assertThat(scheduledFor(mealId)).isEqualTo(1);

        mvc.perform(delete("/api/meals/" + mealId).cookie(jisu)).andExpect(status().isOk());
        assertThat(scheduledFor(mealId)).isZero();

        clock.set(NOW.plus(Duration.ofMinutes(1)));
        mvc.perform(post("/api/meals/" + mealId + "/restore").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealId").value(mealId));
        assertThat(scheduledFor(mealId)).isEqualTo(1);
    }

    @Test
    void 식사를_동시에_두_번_되돌려도_하나만_200_알림도_하나만_예약된다() throws Exception {
        send(post("/api/criteria-versions"), jisu, LUNCH_CRITERIA).andExpect(status().isCreated());
        long mealId = lunchTenMinutesAgo();
        mvc.perform(delete("/api/meals/" + mealId).cookie(jisu)).andExpect(status().isOk());

        assertThat(concurrently("/api/meals/" + mealId + "/restore")).containsExactlyInAnyOrder(200, 409);
        assertThat(scheduledFor(mealId)).isEqualTo(1);
    }

    @Test
    void 식사_되돌리기_실패_이유별_코드() throws Exception {
        long mealId = id(send(post("/api/meals"), jisu,
                "{\"mealSlot\":\"SNACK\",\"startedAt\":\"" + NOW.minus(Duration.ofMinutes(10)) + "\"}")
                .andExpect(status().isCreated()), "$.data.mealId");
        mvc.perform(post("/api/meals/" + mealId + "/restore").cookie(jisu))   // 안 지운 식사
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40903"));

        mvc.perform(delete("/api/meals/" + mealId).cookie(jisu)).andExpect(status().isOk());
        mvc.perform(post("/api/meals/" + mealId + "/restore").cookie(minji))  // 남의 식사
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));

        clock.set(NOW.plus(Duration.ofMinutes(6)));
        mvc.perform(post("/api/meals/" + mealId + "/restore").cookie(jisu))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40904"));
    }

    // 건너뛴 식사는 지웠다 되돌려도 알림을 다시 안 건다 (78 A × 74 B)
    @Test
    void 건너뛴_식사를_지웠다_되돌려도_알림은_다시_안_건다() throws Exception {
        long mealId = lunchTenMinutesAgo();   // 병원 기준이 아직 없어 알림이 하나도 없는 식사
        mvc.perform(post("/api/meals/" + mealId + "/measure-skip").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(delete("/api/meals/" + mealId).cookie(jisu)).andExpect(status().isOk());
        // 그사이 병원 기준이 생겨도 — 가드가 없으면 rescheduleAfterMeal이 새 알림을 건다
        send(post("/api/criteria-versions"), jisu, LUNCH_CRITERIA).andExpect(status().isCreated());

        mvc.perform(post("/api/meals/" + mealId + "/restore").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measureSkippedAt").isNotEmpty());
        assertThat(scheduledFor(mealId)).isZero();
    }

    private long deletedReading() throws Exception {
        long readingId = id(send(post("/api/readings"), jisu, READING).andExpect(status().isCreated()), "$.data.readingId");
        mvc.perform(delete("/api/readings/" + readingId).cookie(jisu)).andExpect(status().isOk());   // 지운 시각 = NOW (고정 시계)
        return readingId;
    }

    private long lunchTenMinutesAgo() throws Exception {
        return id(send(post("/api/meals"), jisu,
                "{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + NOW.minus(Duration.ofMinutes(10)) + "\"}")
                .andExpect(status().isCreated()), "$.data.mealId");
    }

    // 같은 POST를 두 스레드가 같은 순간에 보낸 결과 상태 코드들
    private List<Integer> concurrently(String url) throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mvc.perform(post(url).cookie(jisu)).andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) statuses.add(f.get());
            return statuses;
        } finally {
            pool.shutdown();
        }
    }

    private int scheduledFor(long mealId) {
        return jdbc.queryForObject("SELECT count(*) FROM reminders WHERE meal_id = ? AND status = 'SCHEDULED'",
                Integer.class, mealId);
    }

    private ResultActions send(MockHttpServletRequestBuilder req, Cookie who, String body) throws Exception {
        return mvc.perform(req.cookie(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }
}
