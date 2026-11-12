package com.dangdang.domain.notification;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationInboxFlowTest extends ReminderTestBase {

    @Autowired ReminderScheduler scheduler;

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void loginBoth() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    private Long insert(Long userId, String title, Instant sentAt) {
        return jdbc.queryForObject("INSERT INTO notifications (user_id, type, title, deep_link, sent_at)"
                + " VALUES (?, 'MEASURE', ?, '/today', ?) RETURNING id", Long.class, userId, title, Timestamp.from(sentAt));
    }

    private ResultActions read(Cookie who, Long id) throws Exception {
        return mvc.perform(patch("/api/notifications/" + id + "/read").cookie(who));
    }

    @Test
    void 스케줄러가_보낸_알림이_알림함에_보인다() throws Exception {
        subscribe(jisuId, ENDPOINT);
        Long reminderId = reminderAt(jisuId, NOW);
        scheduler.run();

        mvc.perform(get("/api/notifications").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("MEASURE"))
                .andExpect(jsonPath("$.data.items[0].title").value("혈당 잴 시간이에요"))
                .andExpect(jsonPath("$.data.items[0].body").value("점심 식후 혈당을 재 주세요"))
                .andExpect(jsonPath("$.data.items[0].deepLink").value("/today?sheet=glucose&reminderId=" + reminderId))
                .andExpect(jsonPath("$.data.items[0].sentAt").value("2026-11-09T04:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 최신이_위로_나오고_페이지가_나뉜다() throws Exception {
        Long oldest = insert(jisuId, "처음", NOW);
        Long middle = insert(jisuId, "가운데", NOW.plusSeconds(60));
        Long newest = insert(jisuId, "최신", NOW.plusSeconds(120));

        mvc.perform(get("/api/notifications?page=0&size=2").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].id").value(newest))
                .andExpect(jsonPath("$.data.items[1].id").value(middle))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/notifications?page=1&size=2").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(oldest))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 같은_시각이면_번호가_큰_것이_위로() throws Exception {
        Long first = insert(jisuId, "하나", NOW);
        Long second = insert(jisuId, "둘", NOW);

        mvc.perform(get("/api/notifications").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].id").value(second))
                .andExpect(jsonPath("$.data.items[1].id").value(first));
    }

    @Test
    void 내_알림만_나온다() throws Exception {
        insert(minjiId, "민지 것", NOW);

        mvc.perform(get("/api/notifications").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 페이지_값이_틀리면_400() throws Exception {
        for (String query : List.of("page=-1", "size=0", "size=101", "page=a")) {
            mvc.perform(get("/api/notifications?" + query).cookie(jisu))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void 읽으면_readAt이_찍힌다() throws Exception {
        Long id = insert(jisuId, "하나", NOW);

        read(jisu, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.readAt").value("2026-11-09T04:00:00Z"));
    }

    @Test
    void 다시_읽어도_처음_읽은_시각이_남는다() throws Exception {
        Long id = insert(jisuId, "하나", NOW);
        read(jisu, id);

        clock.set(NOW.plus(Duration.ofHours(1)));
        read(jisu, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").value("2026-11-09T04:00:00Z"));
    }

    @Test
    void 남의_알림이나_없는_번호는_404() throws Exception {
        Long id = insert(jisuId, "하나", NOW);

        read(minji, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_40401"));
        read(jisu, 999999L).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, id)).isNull();
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/1/read")).andExpect(status().isUnauthorized());
    }
}
