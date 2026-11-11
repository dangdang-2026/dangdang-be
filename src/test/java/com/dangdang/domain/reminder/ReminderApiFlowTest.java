package com.dangdang.domain.reminder;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderApiFlowTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void loginBoth() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    private ResultActions open(Cookie who, Long id) throws Exception {
        return mvc.perform(post("/api/reminders/" + id + "/open").cookie(who));
    }

    private ResultActions list(Cookie who, String query) throws Exception {
        return mvc.perform(get("/api/reminders?" + query).cookie(who));
    }

    private Timestamp openedAt(Long id) {
        return jdbc.queryForObject("SELECT opened_at FROM reminders WHERE id = ?", Timestamp.class, id);
    }

    private Long sentReminder() {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);
        scheduler.run();
        return id;
    }

    @Test
    void 보낸_알림을_열면_OPENED() throws Exception {
        Long id = sentReminder();

        open(jisu, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("OPENED"))
                .andExpect(jsonPath("$.data.openedAt").value("2026-11-09T04:00:00Z"));
        assertThat(statusOf(id)).isEqualTo("OPENED");
    }

    @Test
    void 두_번_열어도_처음_연_시각이_남는다() throws Exception {
        Long id = sentReminder();
        open(jisu, id);
        Timestamp first = openedAt(id);

        clock.set(NOW.plus(Duration.ofMinutes(3)));
        open(jisu, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPENED"));

        assertThat(openedAt(id)).isEqualTo(first);
    }

    @Test
    void 이미_끝난_알림을_열어도_200이고_그대로() throws Exception {
        Long id = sentReminder();
        setStatus(id, "DONE");

        open(jisu, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.openedAt").value(nullValue()));

        assertThat(statusOf(id)).isEqualTo("DONE");
        assertThat(openedAt(id)).isNull();
    }

    @Test
    void 아직_안_보냈거나_취소됐거나_건너뛴_알림은_409() throws Exception {
        Long id = reminderAt(jisuId, NOW);

        for (String state : List.of("SCHEDULED", "CANCELED", "SKIPPED", "PAUSED")) {
            setStatus(id, state);
            open(jisu, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("REMINDER_40901"));
            assertThat(statusOf(id)).isEqualTo(state);
        }
    }

    @Test
    void 남의_알림이나_없는_번호는_404() throws Exception {
        Long id = sentReminder();

        open(minji, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REMINDER_40401"));
        open(jisu, 999999L).andExpect(status().isNotFound());

        assertThat(statusOf(id)).isEqualTo("SENT");
    }

    @Test
    void 목록은_한국_날짜_양쪽을_포함한다() throws Exception {
        reminderAt(jisuId, Instant.parse("2026-11-08T14:59:59Z"));   // 한국 11/08 23:59:59 — 빠짐
        Long first = reminderAt(jisuId, Instant.parse("2026-11-08T15:00:00Z"));   // 한국 11/09 00:00
        Long last = reminderAt(jisuId, Instant.parse("2026-11-09T14:59:59Z"));    // 한국 11/09 23:59:59
        reminderAt(jisuId, Instant.parse("2026-11-09T15:00:00Z"));   // 한국 11/10 00:00 — 빠짐

        list(jisu, "from=2026-11-09&to=2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].id").value(last))
                .andExpect(jsonPath("$.data.items[1].id").value(first))
                .andExpect(jsonPath("$.data.items[0].slot").value("LUNCH_AFTER"))
                .andExpect(jsonPath("$.data.items[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.items[0].renotified").value(false))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 페이지를_넘기면_hasNext가_바뀐다() throws Exception {
        reminderAt(jisuId, NOW);
        reminderAt(jisuId, NOW.plusSeconds(60));
        reminderAt(jisuId, NOW.plusSeconds(120));

        list(jisu, "from=2026-11-09&to=2026-11-09&page=0&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        list(jisu, "from=2026-11-09&to=2026-11-09&page=1&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 목록에는_내_것만_나온다() throws Exception {
        reminderAt(minjiId, NOW);

        list(jisu, "from=2026-11-09&to=2026-11-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 날짜나_페이지_값이_틀리면_400() throws Exception {
        for (String query : List.of(
                "to=2026-11-09",
                "from=2026-11-09",
                "from=2026-13-40&to=2026-11-09",
                "from=2026-11-10&to=2026-11-09",
                "from=2026-11-09&to=2026-11-09&page=-1",
                "from=2026-11-09&to=2026-11-09&size=0",
                "from=2026-11-09&to=2026-11-09&size=101",
                "from=2026-11-09&to=2026-11-09&size=abc")) {
            list(jisu, query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/reminders?from=2026-11-09&to=2026-11-09")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reminders/1/open")).andExpect(status().isUnauthorized());
    }
}
