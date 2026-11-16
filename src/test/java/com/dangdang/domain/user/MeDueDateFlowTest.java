package com.dangdang.domain.user;

import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/me 에 출산 예정일·임신 상태·주수 (M-02 · H-14). 고정 시계: 한국 2026-11-09
class MeDueDateFlowTest extends ReminderTestBase {

    Cookie jisu;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
    }

    @Test
    void 예정일을_안_적었으면_셋_다_null() throws Exception {
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.data.pregnancyState").value(nullValue()))
                .andExpect(jsonPath("$.data.pregnancyWeek").value(nullValue()));
    }

    @Test
    void 예정일을_적으면_오늘_주수가_나온다() throws Exception {
        mvc.perform(patch("/api/me/due-date").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueDate\":\"2026-12-20\"}"))
                .andExpect(status().isOk());

        // 12/20까지 41일 남음 → 280 - 41 = 239일 = 34주 1일
        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.dueDate").value("2026-12-20"))
                .andExpect(jsonPath("$.data.pregnancyState").value("PREGNANT"))
                .andExpect(jsonPath("$.data.pregnancyWeek.weeks").value(34))
                .andExpect(jsonPath("$.data.pregnancyWeek.days").value(1));
    }

    @Test
    void 예정일_당일은_40주_0일() throws Exception {
        mvc.perform(patch("/api/me/due-date").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueDate\":\"2026-11-09\"}"));

        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.pregnancyWeek.weeks").value(40))
                .andExpect(jsonPath("$.data.pregnancyWeek.days").value(0));
    }

    @Test
    void 끝난_임신은_보여_주지_않는다() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-10-01', 'ENDED', now(), now())", jisuId);

        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.data.pregnancyState").value(nullValue()));
    }

    @Test
    void 입원_중이면_상태가_HOSPITALIZED() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-11-20', 'HOSPITALIZED', now(), now())", jisuId);

        mvc.perform(get("/api/me").cookie(jisu))
                .andExpect(jsonPath("$.data.pregnancyState").value("HOSPITALIZED"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-11-20"));
    }
}
