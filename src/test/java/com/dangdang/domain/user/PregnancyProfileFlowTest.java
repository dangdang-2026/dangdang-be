package com.dangdang.domain.user;

import com.dangdang.domain.pregnancy.enums.CareMode;
import com.dangdang.domain.user.service.PregnancyProfileService;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 진단일 · 관리 모드 · 연락 기준 메모 (M-02 · M-03 · M-01 · G-05). 고정 시계: 한국 2026-11-09 13:00
class PregnancyProfileFlowTest extends ReminderTestBase {

    @Autowired PregnancyProfileService pregnancyProfileService;

    Cookie jisu;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
    }

    private ResultActions patchJson(String path, String body) throws Exception {
        return mvc.perform(patch(path).cookie(jisu).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int pregnancyCount(Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM pregnancies WHERE user_id = ?", Integer.class, userId);
    }

    private String column(String name, Long userId) {
        return jdbc.queryForObject(
                "SELECT " + name + "::text FROM pregnancies WHERE user_id = ? AND state <> 'ENDED'", String.class, userId);
    }

    @Test
    void 임신_정보가_없으면_me의_careMode는_DIET_진단일과_메모는_null() throws Exception {
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosisDate").value(nullValue()))
                .andExpect(jsonPath("$.data.careMode").value("DIET"))
                .andExpect(jsonPath("$.data.contactMemo").value(nullValue()))
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value(nullValue()));
    }

    @Test
    void 처음_진단일을_적으면_임신_줄을_만들고_me에도_나온다() throws Exception {
        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-24\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.diagnosisDate").value("2026-09-24"))
                .andExpect(jsonPath("$.data.careMode").value("DIET"))
                .andExpect(jsonPath("$.data.contactMemo").value(nullValue()));

        assertThat(pregnancyCount(jisuId)).isEqualTo(1);
        assertThat(column("state", jisuId)).isEqualTo("PREGNANT");
        assertThat(column("due_date", jisuId)).isNull();   // 예정일은 그대로 비어 있다

        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.diagnosisDate").value("2026-09-24"))
                .andExpect(jsonPath("$.data.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.data.pregnancyState").value("PREGNANT"));
    }

    @Test
    void 진단일이_오늘_뒤면_400_오늘은_된다() throws Exception {
        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-11-10\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40003"));
        assertThat(pregnancyCount(jisuId)).isZero();

        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-11-09\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.diagnosisDate").value("2026-11-09"));
    }

    @Test
    void 진단일이_출산_예정일보다_뒤면_400_같은_날은_된다() throws Exception {
        patchJson("/api/me/due-date", "{\"dueDate\":\"2026-11-01\"}").andExpect(status().isOk());

        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-11-05\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40004"));
        assertThat(column("diagnosis_date", jisuId)).isNull();

        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-11-01\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 진단일이_없거나_형식이_틀리면_400() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"diagnosisDate\":null}",
                "{\"diagnosisDate\":\"\"}",
                "{\"diagnosisDate\":\"2026/09/24\"}",
                "{\"diagnosisDate\":\"2026-02-30\"}",
                "{\"diagnosisDate\":20260924}")) {
            patchJson("/api/me/diagnosis-date", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(pregnancyCount(jisuId)).isZero();
    }

    @Test
    void 관리_모드를_바꾸면_me에도_나온다() throws Exception {
        patchJson("/api/me/care-mode", "{\"careMode\":\"INSULIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careMode").value("INSULIN"));
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.careMode").value("INSULIN"));

        patchJson("/api/me/care-mode", "{\"careMode\":\"ORAL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careMode").value("ORAL"));
        assertThat(column("care_mode", jisuId)).isEqualTo("ORAL");
        assertThat(pregnancyCount(jisuId)).isEqualTo(1);
    }

    @Test
    void 관리_모드에_없는_값이면_400() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"careMode\":null}",
                "{\"careMode\":\"PILL\"}",
                "{\"careMode\":\"insulin\"}",
                "{\"careMode\":1}")) {
            patchJson("/api/me/care-mode", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(pregnancyCount(jisuId)).isZero();
    }

    @Test
    void 메모는_앞뒤_공백을_지우고_저장하고_빈_글이면_지운다() throws Exception {
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"  같은 시점에 계속 높게 나오면 외래로 전화하기  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemo").value("같은 시점에 계속 높게 나오면 외래로 전화하기"));
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.contactMemo").value("같은 시점에 계속 높게 나오면 외래로 전화하기"));

        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemo").value(nullValue()));
        assertThat(column("contact_memo", jisuId)).isNull();

        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"전화하기\"}").andExpect(status().isOk());
        patchJson("/api/me/contact-memo", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemo").value(nullValue()));
    }

    @Test
    void 메모는_100자까지_되고_101자나_줄바꿈은_400() throws Exception {
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"" + "가".repeat(100) + "\"}")
                .andExpect(status().isOk());

        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"" + "가".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"높으면\\n전화\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));

        assertThat(column("contact_memo", jisuId)).isEqualTo("가".repeat(100));   // 실패한 요청은 안 덮어쓴다
    }

    @Test
    void 세_값은_서로_덮어쓰지_않고_예정일도_그대로다() throws Exception {
        patchJson("/api/me/due-date", "{\"dueDate\":\"2026-12-20\"}").andExpect(status().isOk());
        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-24\"}").andExpect(status().isOk());
        patchJson("/api/me/care-mode", "{\"careMode\":\"INSULIN\"}").andExpect(status().isOk());
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"외래로 전화하기\"}")
                .andExpect(jsonPath("$.data.diagnosisDate").value("2026-09-24"))
                .andExpect(jsonPath("$.data.careMode").value("INSULIN"))
                .andExpect(jsonPath("$.data.contactMemo").value("외래로 전화하기"));

        patchJson("/api/me/due-date", "{\"dueDate\":\"2026-12-27\"}").andExpect(status().isOk());

        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.dueDate").value("2026-12-27"))
                .andExpect(jsonPath("$.data.diagnosisDate").value("2026-09-24"))
                .andExpect(jsonPath("$.data.careMode").value("INSULIN"))
                .andExpect(jsonPath("$.data.contactMemo").value("외래로 전화하기"));
        assertThat(pregnancyCount(jisuId)).isEqualTo(1);
        assertThat(pregnancyCount(minjiId)).isZero();   // 민지 것은 안 생긴다
    }

    @Test
    void 산후여도_진행_중인_줄을_고친다() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, delivery_date, state)"
                + " VALUES (?, '2026-11-20', '2026-11-05', 'POSTPARTUM')", jisuId);

        patchJson("/api/me/care-mode", "{\"careMode\":\"INSULIN\"}").andExpect(status().isOk());

        assertThat(pregnancyCount(jisuId)).isEqualTo(1);
        assertThat(column("care_mode", jisuId)).isEqualTo("INSULIN");
        assertThat(column("state", jisuId)).isEqualTo("POSTPARTUM");
    }

    @Test
    void 졸업했으면_세_API_모두_409() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state) VALUES (?, '2026-10-01', 'ENDED')", jisuId);

        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-01\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40902"));
        patchJson("/api/me/care-mode", "{\"careMode\":\"INSULIN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40902"));
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"전화하기\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40902"));

        assertThat(pregnancyCount(jisuId)).isEqualTo(1);   // 끝난 줄만 그대로
    }

    @Test
    void 처음_저장을_동시에_여러_번_해도_임신_줄은_하나() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return pregnancyProfileService.changeCareMode(jisuId, CareMode.INSULIN);
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get();   // 하나라도 실패했으면 여기서 예외가 나서 테스트가 실패한다
        }
        pool.shutdown();

        assertThat(pregnancyCount(jisuId)).isEqualTo(1);
    }

    @Test
    void DB도_관리_모드_세_값만_받는다() {
        jdbc.update("INSERT INTO pregnancies (user_id, state) VALUES (?, 'PREGNANT')", jisuId);

        assertThatThrownBy(() -> jdbc.update("UPDATE pregnancies SET care_mode = 'PILL' WHERE user_id = ?", jisuId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(column("care_mode", jisuId)).isEqualTo("DIET");   // 기본값
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        for (String[] call : List.of(
                new String[]{"/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-24\"}"},
                new String[]{"/api/me/care-mode", "{\"careMode\":\"INSULIN\"}"},
                new String[]{"/api/me/contact-memo", "{\"contactMemo\":\"전화하기\"}"})) {
            mvc.perform(patch(call[0]).contentType(MediaType.APPLICATION_JSON).content(call[1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_40101"));
        }
    }

    // ---------- 연락 기준 메모 적은 시각 (G-05 「10월 1일에 적은 메모」) — 79 C · 합치며 바뀜 ----------

    @Test
    void 연락_메모를_적으면_적은_시각이_오고_지우면_null() throws Exception {
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"70 미만이면 단 것을 먹고 15분 뒤 다시 재기\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value("2026-11-09T04:00:00Z"));
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value("2026-11-09T04:00:00Z"));

        patchJson("/api/me/contact-memo", "{\"contactMemo\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value(nullValue()));
        assertThat(column("contact_memo_updated_at", jisuId)).isNull();
    }

    @Test
    void 같은_메모를_다시_저장하면_적은_시각은_그대로_글이_바뀌면_새_시각() throws Exception {
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"외래로 전화하기\"}").andExpect(status().isOk());

        clock.set(NOW.plusSeconds(300));   // 5분 뒤 (토큰이 살아 있는 짧은 시간)
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"  외래로 전화하기 \"}")   // 공백을 지우면 같은 글
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value("2026-11-09T04:00:00Z"));
        patchJson("/api/me/care-mode", "{\"careMode\":\"INSULIN\"}")   // 다른 칸을 바꿔도 그대로
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value("2026-11-09T04:00:00Z"));

        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"분만실로 전화하기\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value("2026-11-09T04:05:00Z"));
    }

    // ---------- 다른 상황 · 동시 수정 · 건강정보 동의 ----------

    @Test
    void 다른_상황이어도_진행_중인_줄을_고친다() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state) VALUES (?, '2026-12-20', 'PAUSED')", jisuId);

        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"외래로 전화하기\"}").andExpect(status().isOk());

        assertThat(pregnancyCount(jisuId)).isEqualTo(1);
        assertThat(column("contact_memo", jisuId)).isEqualTo("외래로 전화하기");
        assertThat(column("state", jisuId)).isEqualTo("PAUSED");
    }

    @Test
    void 관리_모드와_메모를_동시에_바꿔도_서로_덮어쓰지_않는다() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, state) VALUES (?, 'PREGNANT')", jisuId);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            boolean careMode = i % 2 == 0;
            results.add(pool.submit(() -> {
                start.await();
                return careMode
                        ? pregnancyProfileService.changeCareMode(jisuId, CareMode.ORAL)
                        : pregnancyProfileService.changeContactMemo(jisuId, "외래로 전화하기");
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get();
        }
        pool.shutdown();

        assertThat(column("care_mode", jisuId)).isEqualTo("ORAL");
        assertThat(column("contact_memo", jisuId)).isEqualTo("외래로 전화하기");
    }

    @Test
    void 건강정보_동의가_없으면_세_API_모두_403이고_줄을_안_만든다() throws Exception {
        jdbc.update("DELETE FROM consent_events WHERE user_id = ? AND consent_type = 'SENSITIVE'", jisuId);

        for (String[] call : List.of(
                new String[]{"/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-24\"}"},
                new String[]{"/api/me/care-mode", "{\"careMode\":\"INSULIN\"}"},
                new String[]{"/api/me/contact-memo", "{\"contactMemo\":\"전화하기\"}"})) {
            patchJson(call[0], call[1])
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("USER_40302"));
        }
        assertThat(pregnancyCount(jisuId)).isZero();
    }

    @Test
    void 건강정보_동의를_철회하면_세_값도_지워진다() throws Exception {
        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-24\"}").andExpect(status().isOk());
        patchJson("/api/me/care-mode", "{\"careMode\":\"INSULIN\"}").andExpect(status().isOk());
        patchJson("/api/me/contact-memo", "{\"contactMemo\":\"외래로 전화하기\"}").andExpect(status().isOk());

        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu)).andExpect(status().isOk());

        assertThat(pregnancyCount(jisuId)).isZero();
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.diagnosisDate").value(nullValue()))
                .andExpect(jsonPath("$.data.careMode").value("DIET"))
                .andExpect(jsonPath("$.data.contactMemo").value(nullValue()))
                .andExpect(jsonPath("$.data.contactMemoUpdatedAt").value(nullValue()));
    }

    // ---------- 날짜 한계 · 예정일과의 관계 · 한 줄 검사 ----------

    @Test
    void 진단일은_2000년부터_된다() throws Exception {
        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"1999-12-31\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        assertThat(pregnancyCount(jisuId)).isZero();

        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2000-01-01\"}").andExpect(status().isOk());
    }

    @Test
    void 예정일을_진단일보다_앞으로_바꾸면_400_같은_날은_된다() throws Exception {
        patchJson("/api/me/diagnosis-date", "{\"diagnosisDate\":\"2026-09-24\"}").andExpect(status().isOk());

        patchJson("/api/me/due-date", "{\"dueDate\":\"2026-09-23\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PREGNANCY_40004"));
        assertThat(column("due_date", jisuId)).isNull();

        patchJson("/api/me/due-date", "{\"dueDate\":\"2026-09-24\"}").andExpect(status().isOk());
        assertThat(column("due_date", jisuId)).isEqualTo("2026-09-24");
    }

    @Test
    void 메모_가운데_줄_구분_글자가_있으면_400() throws Exception {
        for (String sep : List.of("\\u2028", "\\u2029", "\\u0085", "\\r", "\\t")) {
            patchJson("/api/me/contact-memo", "{\"contactMemo\":\"높으면" + sep + "전화\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(pregnancyCount(jisuId)).isZero();
    }
}
