package com.dangdang.domain.criteria;

import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 지난 날짜부터 적용하는 병원 기준 (C-09 「10월 19일부터 적용」) → 그 사이 이미 잰 기록도 새 기준으로 판정
// 시계는 2026-11-09 한국 13:00 고정 (ReminderTestBase)
class CriteriaBackdateFlowTest extends ReminderTestBase {

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    @Test
    void 지난_날짜_기준은_적용일부터_다음_기준_전까지_기록을_다시_붙인다() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-01\"}");    // v1
        criteria(jisu, "{\"fastingTarget\":90,\"effectiveFrom\":\"2026-11-07\"}");    // 다음 기준
        long before = reading(jisu, "2026-11-02T08:00:00+09:00");    // 새 기준 적용일 전 → v1 그대로
        long first = reading(jisu, "2026-11-03T00:00:00+09:00");     // 적용일 0시 → 새 기준
        long last = reading(jisu, "2026-11-06T23:59:00+09:00");      // 다음 기준 전날 끝 → 새 기준
        long after = reading(jisu, "2026-11-07T00:00:00+09:00");     // 다음 기준 시작 → 그대로

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-11-03\"}");

        assertThat(fastingTargetOf(before)).isEqualTo(95);
        assertThat(fastingTargetOf(first)).isEqualTo(100);
        assertThat(fastingTargetOf(last)).isEqualTo(100);
        assertThat(fastingTargetOf(after)).isEqualTo(90);
        // 응답 판정도 새 기준으로 (96은 100 미만 → 목표 안)
        mvc.perform(get("/api/readings/" + first).cookie(jisu))
                .andExpect(jsonPath("$.data.targetValue").value(100))
                .andExpect(jsonPath("$.data.aboveTarget").value(false));
    }

    @Test
    void 다음_기준이_없으면_그_뒤_기록을_모두_다시_붙인다() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-01\"}");
        long yesterday = reading(jisu, "2026-11-08T08:00:00+09:00");
        long today = reading(jisu, "2026-11-09T08:00:00+09:00");
        long lateToday = reading(jisu, "2026-11-09T12:59:00+09:00");   // 오늘 늦은 시각(지금 13:00 직전)도 새 기준

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-11-08\"}");

        assertThat(fastingTargetOf(yesterday)).isEqualTo(100);
        assertThat(fastingTargetOf(today)).isEqualTo(100);
        assertThat(fastingTargetOf(lateToday)).isEqualTo(100);
    }

    @Test
    void 오늘이나_비운_적용일이면_오늘_이미_잰_기록은_그대로() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-01\"}");
        long today = reading(jisu, "2026-11-09T08:00:00+09:00");

        criteria(jisu, "{\"fastingTarget\":100}");                                   // 비우면 오늘
        criteria(jisu, "{\"fastingTarget\":110,\"effectiveFrom\":\"2026-11-09\"}");   // 오늘

        assertThat(fastingTargetOf(today)).isEqualTo(95);   // 오늘 진료 전에 잰 기록은 그때 기준 (C-10)
    }

    @Test
    void 지운_기록도_다시_붙이고_수정됨은_안_생기고_row_version은_오른다() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-01\"}");
        long deleted = reading(jisu, "2026-11-05T08:00:00+09:00");
        mvc.perform(delete("/api/readings/" + deleted).cookie(jisu)).andExpect(status().isOk());
        long alive = reading(jisu, "2026-11-06T08:00:00+09:00");
        long versionBefore = rowVersion(alive);

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-11-04\"}");

        assertThat(fastingTargetOf(deleted)).isEqualTo(100);   // 되돌리면 새 기준으로 보이게
        assertThat(fastingTargetOf(alive)).isEqualTo(100);
        assertThat(rowVersion(alive)).isEqualTo(versionBefore + 1);   // 그사이 같은 기록을 고치던 요청은 409가 되게
        assertThat(jdbc.queryForObject("SELECT count(*) FROM readings WHERE edited_at IS NOT NULL", Long.class))
                .isZero();                                          // 사용자가 고친 게 아니라 「수정됨」 아님
        mvc.perform(get("/api/readings/" + alive).cookie(jisu))
                .andExpect(jsonPath("$.data.editedAt").value((Object) null));
    }

    @Test
    void 남의_기록은_다시_붙이지_않는다() throws Exception {
        criteria(minji, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-01\"}");
        long others = reading(minji, "2026-11-05T08:00:00+09:00");

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-11-04\"}");

        assertThat(fastingTargetOf(others)).isEqualTo(95);
    }

    @Test
    void 지난_날짜_기준을_동시에_두_번_적어도_막히지_않고_나중_것이_이긴다() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-01\"}");
        long r = reading(jisu, "2026-11-05T08:00:00+09:00");

        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            return mvc.perform(post("/api/criteria-versions").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fastingTarget\":100,\"effectiveFrom\":\"2026-11-04\"}"))
                    .andReturn().getResponse().getStatus();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(pool.submit(task), pool.submit(task));
            start.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get()).isEqualTo(201);   // 사용자 줄을 FOR UPDATE로 먼저 잡아서 서로 기다리다 막히지 않음
            }
        } finally {
            pool.shutdownNow();
        }
        // 같은 적용일이면 id 큰 것이 지금 기준 → 기록도 그 기준에 붙어 있어야 함
        Long newest = jdbc.queryForObject("SELECT max(id) FROM criteria_versions WHERE user_id = ?", Long.class, jisuId);
        assertThat(jdbc.queryForObject("SELECT criteria_version_id FROM readings WHERE id = ?", Long.class, r))
                .isEqualTo(newest);
    }

    @Test
    void 기준_없이_잰_기록의_빈_기준은_다음_기준으로_안_치고_지난_날짜_기준이_덮으면_지운다() throws Exception {
        long first = reading(jisu, "2026-11-01T08:00:00+09:00");     // 기준 없음 → 빈 기준 E(11/1) 생김
        long middle = reading(jisu, "2026-11-05T08:00:00+09:00");
        long deleted = reading(jisu, "2026-11-08T08:00:00+09:00");
        mvc.perform(delete("/api/readings/" + deleted).cookie(jisu)).andExpect(status().isOk());
        assertThat(criteriaCount()).isEqualTo(1);

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-10-19\"}");   // X: 10/19부터

        assertThat(fastingTargetOf(first)).isEqualTo(100);
        assertThat(fastingTargetOf(middle)).isEqualTo(100);
        assertThat(fastingTargetOf(deleted)).isEqualTo(100);   // 지운 혈당도 옮겨서 빈 기준을 지울 수 있음
        assertThat(criteriaCount()).isEqualTo(1);              // 빈 기준은 지워지고 X만
        // 지금 기준·새 혈당·리포트 기준 목록 모두 X
        mvc.perform(get("/api/criteria-versions/current").cookie(jisu))
                .andExpect(jsonPath("$.data.fastingTarget").value(100));
        assertThat(fastingTargetOf(reading(jisu, "2026-11-09T08:00:00+09:00"))).isEqualTo(100);
        long reportId = customReport("2026-11-01", "2026-11-09");
        mvc.perform(get("/api/visit-reports/" + reportId).cookie(jisu))
                .andExpect(jsonPath("$.data.criteria", hasSize(1)))
                .andExpect(jsonPath("$.data.criteria[0].fastingTarget").value(100))
                .andExpect(jsonPath("$.data.summary.outOfTarget").value(0));   // 96 < 100
    }

    @Test
    void 기준이_있어도_앞_날짜_기록으로_생긴_빈_기준은_더_앞_날짜_기준이_덮는다() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95,\"effectiveFrom\":\"2026-11-05\"}");   // A
        long early = reading(jisu, "2026-10-25T08:00:00+09:00");     // A 앞 → 빈 기준 E(10/25)
        long afterA = reading(jisu, "2026-11-06T08:00:00+09:00");    // A

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-10-19\"}");  // X: 10/19 ~ A 전

        assertThat(fastingTargetOf(early)).isEqualTo(100);
        assertThat(fastingTargetOf(afterA)).isEqualTo(95);
        assertThat(criteriaCount()).isEqualTo(2);   // A, X (빈 기준 지움)
        mvc.perform(get("/api/criteria-versions/current").cookie(jisu))
                .andExpect(jsonPath("$.data.fastingTarget").value(95));
    }

    @Test
    void 아직_혈당이_가리키는_빈_기준은_남기고_등록은_성공한다() throws Exception {
        reading(jisu, "2026-11-01T08:00:00+09:00");                   // 빈 기준 E(11/1)
        long todayOnEmpty = reading(jisu, "2026-11-09T08:00:00+09:00");   // 오늘 기준 적기 전 → E
        criteria(jisu, "{\"fastingTarget\":95}");                    // 오늘 N (오늘 기록은 안 바꿈)

        criteria(jisu, "{\"fastingTarget\":100,\"effectiveFrom\":\"2026-10-19\"}");  // X: 10/19 ~ N 전

        assertThat(fastingTargetOf(todayOnEmpty)).isNull();   // 그때 기준(빈 기준) 그대로 → 외래키에 안 걸리게 E는 남김
        assertThat(criteriaCount()).isEqualTo(3);
    }

    @Test
    void 오늘_같은_날_기준을_두_번_고치면_기록이_판정에_쓴_기준도_리포트_목록에_나온다() throws Exception {
        criteria(jisu, "{\"fastingTarget\":95}");                    // 오늘 v1
        reading(jisu, "2026-11-09T08:00:00+09:00");                   // v1로 판정 → 밖
        criteria(jisu, "{\"fastingTarget\":100}");                   // 오늘 v2 (오늘이라 다시 안 붙임)

        long reportId = customReport("2026-11-09", "2026-11-09");
        mvc.perform(get("/api/visit-reports/" + reportId).cookie(jisu))
                .andExpect(jsonPath("$.data.readings[0].aboveTarget").value(true))
                .andExpect(jsonPath("$.data.criteria", hasSize(2)))
                .andExpect(jsonPath("$.data.criteria[0].fastingTarget").value(95))
                .andExpect(jsonPath("$.data.criteria[1].fastingTarget").value(100));
    }

    // ---------- 도우미 ----------

    private void criteria(Cookie who, String json) throws Exception {
        mvc.perform(post("/api/criteria-versions").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    // 공복 96 (지난 기준 95면 목표 밖, 100이면 목표 안)
    private long reading(Cookie who, String measuredAt) throws Exception {
        String body = mvc.perform(post("/api/readings").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":96,\"measuredAt\":\"" + measuredAt + "\",\"readingType\":\"FASTING\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.readingId")).longValue();
    }

    private long criteriaCount() {
        return jdbc.queryForObject("SELECT count(*) FROM criteria_versions WHERE user_id = ?", Long.class, jisuId);
    }

    private long customReport(String from, String to) throws Exception {
        String body = mvc.perform(post("/api/visit-reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CUSTOM\",\"from\":\"" + from + "\",\"to\":\"" + to + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private Integer fastingTargetOf(long readingId) {
        return jdbc.queryForObject("SELECT c.fasting_target FROM readings r"
                + " JOIN criteria_versions c ON c.id = r.criteria_version_id WHERE r.id = ?", Integer.class, readingId);
    }

    private long rowVersion(long readingId) {
        return jdbc.queryForObject("SELECT row_version FROM readings WHERE id = ?", Long.class, readingId);
    }
}
