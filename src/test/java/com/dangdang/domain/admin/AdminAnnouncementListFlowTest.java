package com.dangdang.domain.admin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 당당 소식 목록 (운영 v2 · 당당 소식). 지금은 2026-11-09(월) 한국 13:00
class AdminAnnouncementListFlowTest extends ReminderTestBase {

    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), clock.instant()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM content_cards");
    }

    private static Timestamp at(String iso) {
        return iso == null ? null : Timestamp.from(Instant.parse(iso));
    }

    private Long news(String slot, String kind, String title, String from, String to, Long cardId) {
        return jdbc.queryForObject("INSERT INTO announcements (slot, kind, title, active_from, active_to, content_card_id, reviewed_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                slot, kind, title, at(from), at(to), cardId, "AD".equals(kind) ? "다현" : null);
    }

    // 카드는 API로 만들고 올린다 (만들기·올리기 규칙을 그대로 지나가게)
    private Long publishedCard() throws Exception {
        String res = mvc.perform(post("/api/admin/cards").cookie(admin).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"식후 혈당, 언제 재나요?","series":"GUIDELINE",
                         "body":[{"title":"식후 혈당","text":"병원에서 정해 준 시간에 재요."},{"title":"1시간","text":"식사 시작부터 셉니다."}],
                         "sources":[{"title":"대한당뇨병학회","url":"https://www.diabetes.or.kr"}],
                         "checkedOn":"2026-11-01"}"""))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(res, "$.data.id")).longValue();
        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());
        return id;
    }

    private void seed() throws Exception {
        Long card = publishedCard();
        news("SPLASH", "NEWS", "식후 혈당, 언제 재나요?", "2026-11-05T00:00:00Z", null, card);          // 올라가 있음
        news("COMMUNITY", "AD", "임산부 바디필로우 기획전", "2026-11-01T00:00:00Z", "2026-11-30T15:00:00Z", null);   // 올라가 있음
        news("SPLASH", "NEWS", "12월 정기검진 안내", "2026-11-10T00:00:00Z", null, null);                // 예약 (내일 9:00)
        news("SPLASH", "NEWS", "11월 혈당 기록 이벤트", "2026-10-01T00:00:00Z", "2026-11-08T00:00:00Z", null);   // 끝남
        news("SPLASH", "NEWS", "취소한 예약", "2026-11-12T00:00:00Z", "2026-11-12T00:00:00Z", null);     // 시작 전에 내림 = 예약 취소
    }

    @Test
    void 전체는_올라가_있음_예약_끝남_순서() throws Exception {
        seed();
        mvc.perform(get("/api/admin/announcements").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liveCount").value(2))
                .andExpect(jsonPath("$.data.scheduledCount").value(1))
                .andExpect(jsonPath("$.data.endedCount").value(2))
                // 자리 현황 — 앱 첫 화면 1/3칸, 커뮤니티 위 1/1칸
                .andExpect(jsonPath("$.data.slots[0].slot").value("SPLASH"))
                .andExpect(jsonPath("$.data.slots[0].used").value(1))
                .andExpect(jsonPath("$.data.slots[0].max").value(3))
                .andExpect(jsonPath("$.data.slots[1].slot").value("COMMUNITY"))
                .andExpect(jsonPath("$.data.slots[1].used").value(1))
                .andExpect(jsonPath("$.data.slots[1].full").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(5)))
                .andExpect(jsonPath("$.data.items[0].title").value("식후 혈당, 언제 재나요?"))
                .andExpect(jsonPath("$.data.items[0].state").value("LIVE"))
                .andExpect(jsonPath("$.data.items[0].card.slideCount").value(2))
                .andExpect(jsonPath("$.data.items[0].card.sourceCount").value(1))
                .andExpect(jsonPath("$.data.items[1].kind").value("AD"))
                .andExpect(jsonPath("$.data.items[1].reviewedBy").value("다현"))
                .andExpect(jsonPath("$.data.items[2].title").value("12월 정기검진 안내"))
                .andExpect(jsonPath("$.data.items[2].state").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.items[3].state").value("ENDED"))
                .andExpect(jsonPath("$.data.items[4].title").value("취소한 예약"))
                .andExpect(jsonPath("$.data.items[4].state").value("CANCELED"));
    }

    @Test
    void 탭으로_거른다() throws Exception {
        seed();
        mvc.perform(get("/api/admin/announcements").param("state", "SCHEDULED").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].title").value("12월 정기검진 안내"));
        mvc.perform(get("/api/admin/announcements").param("state", "ENDED").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)));   // 끝남 + 예약 취소
    }

    @Test
    void 예약을_취소하면_예약_숫자에서_빠진다() throws Exception {
        Long id = news("SPLASH", "NEWS", "12월 정기검진 안내", "2026-11-10T00:00:00Z", null, null);
        mvc.perform(post("/api/admin/announcements/" + id + "/end").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/announcements").cookie(admin))
                .andExpect(jsonPath("$.data.scheduledCount").value(0))
                .andExpect(jsonPath("$.data.items[0].state").value("CANCELED"));
        mvc.perform(get("/api/admin/dashboard").cookie(admin))
                .andExpect(jsonPath("$.data.todo.scheduledAnnouncements").value(0))
                .andExpect(jsonPath("$.data.todo.nextAnnouncementAt").doesNotExist());
    }

    @Test
    void 운영자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/announcements").cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden());
    }
}
