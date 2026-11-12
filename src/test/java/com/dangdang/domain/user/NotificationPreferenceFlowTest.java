package com.dangdang.domain.user;

import com.dangdang.domain.reminder.scheduler.ReminderScheduler;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationPreferenceFlowTest extends ReminderTestBase {

    static final String URL = "/api/me/notification-preferences";

    @Autowired ReminderScheduler scheduler;

    Cookie jisu;

    @BeforeEach
    void loginJisu() throws Exception {
        jisu = login("jisu@x.com");
    }

    private ResultActions change(String json) throws Exception {
        return mvc.perform(patch(URL).cookie(jisu).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT count(*) FROM notification_preferences", Integer.class);
    }

    @Test
    void 저장한_적_없으면_기본값으로_답하고_줄을_만들지_않는다() throws Exception {
        mvc.perform(get(URL).cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measurementEnabled").value(true))
                .andExpect(jsonPath("$.data.scheduleEnabled").value(true))
                .andExpect(jsonPath("$.data.hideContent").value(true))
                .andExpect(jsonPath("$.data.renotifyEnabled").value(true))
                .andExpect(jsonPath("$.data.fastingAlarmTime").value("07:00"))
                .andExpect(jsonPath("$.data.bedtimeAlarmTime").value("22:00"))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));

        assertThat(rowCount()).isZero();
    }

    @Test
    void 처음_바꾸면_줄이_생기고_보낸_칸만_바뀐다() throws Exception {
        change("{\"measurementEnabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.measurementEnabled").value(false))
                .andExpect(jsonPath("$.data.scheduleEnabled").value(true))
                .andExpect(jsonPath("$.data.hideContent").value(true))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void 두_번째_변경은_같은_줄을_바꾸고_앞에서_바꾼_칸은_유지한다() throws Exception {
        change("{\"scheduleEnabled\":false,\"fastingAlarmTime\":\"06:30\"}").andExpect(status().isOk());

        change("{\"hideContent\":false,\"renotifyEnabled\":false,\"bedtimeAlarmTime\":\"23:10\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduleEnabled").value(false))
                .andExpect(jsonPath("$.data.fastingAlarmTime").value("06:30"))
                .andExpect(jsonPath("$.data.hideContent").value(false))
                .andExpect(jsonPath("$.data.renotifyEnabled").value(false))
                .andExpect(jsonPath("$.data.bedtimeAlarmTime").value("23:10"))
                .andExpect(jsonPath("$.data.measurementEnabled").value(true));

        assertThat(rowCount()).isEqualTo(1);
        mvc.perform(get(URL).cookie(jisu)).andExpect(jsonPath("$.data.hideContent").value(false));
    }

    @Test
    void 빈_객체나_null이나_틀린_값이면_400() throws Exception {
        for (String json : List.of(
                "{}",
                "null",
                "{\"measurementEnabled\":null}",
                "{\"measurementEnabled\":null,\"hideContent\":true}",
                "{\"measurementEnabled\":\"yes\"}",
                "{\"fastingAlarmTime\":\"25:00\"}",
                "{\"fastingAlarmTime\":\"7시\"}",
                "{\"unknownField\":true}")) {
            change(json)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        assertThat(rowCount()).isZero();
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"hideContent\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 측정_알림을_끄면_스케줄러가_SKIPPED로_넘긴다() throws Exception {
        subscribe(jisuId, ENDPOINT);
        Long id = reminderAt(jisuId, NOW);

        change("{\"measurementEnabled\":false}").andExpect(status().isOk());
        scheduler.run();

        assertThat(statusOf(id)).isEqualTo("SKIPPED");
        assertThat(fakeSender.sent).isEmpty();
        assertThat(inboxCount()).isZero();
    }

    @Test
    void 측정_알림을_다시_켜면_다음_예약부터_보낸다() throws Exception {
        subscribe(jisuId, ENDPOINT);
        change("{\"measurementEnabled\":false}");
        Long off = reminderAt(jisuId, NOW);
        scheduler.run();

        change("{\"measurementEnabled\":true}");
        Long on = reminderAt(jisuId, NOW);
        scheduler.run();

        assertThat(statusOf(off)).isEqualTo("SKIPPED");
        assertThat(statusOf(on)).isEqualTo("SENT");
    }

    @Test
    void 재알림을_끄면_10분이_지나도_안_보낸다() throws Exception {
        subscribe(jisuId, ENDPOINT);
        reminderAt(jisuId, NOW);
        scheduler.run();
        fakeSender.sent.clear();

        change("{\"renotifyEnabled\":false}").andExpect(status().isOk());
        clock.set(NOW.plus(Duration.ofMinutes(10)));
        scheduler.run();

        assertThat(fakeSender.sent).isEmpty();
    }

    @Test
    void hideContent를_끄면_푸시에_원래_문구가_간다() throws Exception {
        subscribe(jisuId, ENDPOINT);
        reminderAt(jisuId, NOW);

        change("{\"hideContent\":false}").andExpect(status().isOk());
        scheduler.run();

        assertThat(fakeSender.sent.get(0).body()).isEqualTo("혈당 잴 시간이에요");
    }
}
