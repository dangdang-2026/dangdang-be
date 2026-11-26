package com.dangdang.domain.admin;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 처리 기록 (운영 v2 · 처리 기록). 운영자 다현이 1분 간격으로 일을 한다
class AdminLogFlowTest extends ReminderTestBase {

    Cookie admin;
    Instant t = NOW;

    @BeforeEach
    void setUp() throws Exception {
        Long adminId = userRepository.save(User.ofEmail("admin@x.com", "다현", passwordEncoder.encode("abcd1234"), clock.instant())).getId();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", adminId);
        admin = login("admin@x.com");
        jdbc.update("INSERT INTO community_profiles (user_id, nickname) VALUES (?, '봄날맘')", jisuId);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM admin_logs");
        jdbc.update("DELETE FROM admin_actions");
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM content_cards");
    }

    private void tick() {
        t = t.plusSeconds(60);
        clock.set(t);
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long idOf(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    // 다현이 한 일: 제한 걸기 → 소식 예약 → 예약 취소 → 카드 만들기 → 올리기 → 제한 풀기 → 글 가리기
    private void work() throws Exception {
        tick();
        long sanction = idOf(postJson("/api/admin/sanctions", "{\"userId\":" + jisuId + ",\"reason\":\"AD_SALES\",\"days\":7}")
                .andExpect(status().isCreated()));
        tick();
        long news = idOf(postJson("/api/admin/announcements",
                "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"12월 정기검진 안내\",\"activeFrom\":\"2026-11-10T00:00:00Z\"}")
                .andExpect(status().isCreated()));
        tick();
        mvc.perform(post("/api/admin/announcements/" + news + "/end").cookie(admin)).andExpect(status().isOk());
        tick();
        long card = idOf(postJson("/api/admin/cards", """
                {"title":"식후 혈당, 언제 재나요?","series":"GUIDELINE",
                 "body":[{"title":"식후 혈당","text":"병원에서 정해 준 시간에 재요."}],
                 "sources":[{"title":"대한당뇨병학회","url":"https://www.diabetes.or.kr"}],
                 "checkedOn":"2026-11-01"}""").andExpect(status().isCreated()));
        tick();
        mvc.perform(post("/api/admin/cards/" + card + "/publish").cookie(admin)).andExpect(status().isOk());
        tick();
        mvc.perform(post("/api/admin/sanctions/" + sanction + "/lift").cookie(admin)).andExpect(status().isOk());
        tick();
        // 신고 처리는 원래 admin_actions 에 남는다 — 처리 기록 목록이 둘을 합쳐 보여 준다
        Long profile = jdbc.queryForObject("SELECT id FROM community_profiles WHERE user_id = ?", Long.class, jisuId);
        Long post = jdbc.queryForObject("INSERT INTO posts (profile_id, category, title, content) VALUES (?, 'MIND', '이 약 두 알 먹으면 괜찮아요', '본문') RETURNING id",
                Long.class, profile);
        jdbc.update("INSERT INTO reports (target_type, target_id, reason) VALUES ('POST', ?, 'MEDICAL_ADVICE')", post);
        postJson("/api/admin/reports/POST/" + post + "/hide", "{\"reason\":\"MEDICAL_ADVICE\",\"memo\":\"약 이름 권유\"}")
                .andExpect(status().isOk());
    }

    @Test
    void 운영자가_한_일이_최근_것부터_다_남는다() throws Exception {
        work();
        mvc.perform(get("/api/admin/logs").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(7)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].action").value("REPORT_HIDE"))
                .andExpect(jsonPath("$.data.items[0].category").value("REPORT"))
                .andExpect(jsonPath("$.data.items[0].targetLabel").value("이 약 두 알 먹으면 괜찮아요"))
                .andExpect(jsonPath("$.data.items[0].targetAuthor").value("봄날맘"))
                .andExpect(jsonPath("$.data.items[0].reason").value("MEDICAL_ADVICE"))
                .andExpect(jsonPath("$.data.items[0].memo").value("약 이름 권유"))
                .andExpect(jsonPath("$.data.items[0].adminName").value("다현"))
                .andExpect(jsonPath("$.data.items[1].action").value("SANCTION_LIFT"))
                .andExpect(jsonPath("$.data.items[1].targetLabel").value("봄날맘"))
                .andExpect(jsonPath("$.data.items[2].action").value("CARD_PUBLISH"))
                .andExpect(jsonPath("$.data.items[3].action").value("CARD_CREATE"))
                .andExpect(jsonPath("$.data.items[3].memo").value("1장"))
                .andExpect(jsonPath("$.data.items[4].action").value("ANNOUNCEMENT_END"))
                .andExpect(jsonPath("$.data.items[4].memo").value("예약 취소"))
                .andExpect(jsonPath("$.data.items[5].action").value("ANNOUNCEMENT_CREATE"))
                .andExpect(jsonPath("$.data.items[5].memo").value("앱 첫 화면 · 소식 · 예약"))
                .andExpect(jsonPath("$.data.items[6].action").value("SANCTION_CREATE"))
                .andExpect(jsonPath("$.data.items[6].reason").value("AD_SALES"))
                .andExpect(jsonPath("$.data.items[6].memo").value("7일"));
    }

    @Test
    void 종류와_검색어로_거른다() throws Exception {
        work();
        mvc.perform(get("/api/admin/logs").param("category", "SANCTION").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mvc.perform(get("/api/admin/logs").param("category", "CARD").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        // 닉네임으로 찾으면 그 사람 제한 기록과 그 사람 글 가리기가 같이 나온다
        mvc.perform(get("/api/admin/logs").param("q", "봄날").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(3)));
        mvc.perform(get("/api/admin/logs").param("category", "ANNOUNCEMENT").param("q", "하늘콩").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 쪽으로_나눠_준다() throws Exception {
        work();
        mvc.perform(get("/api/admin/logs").param("size", "5").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(5)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/admin/logs").param("size", "5").param("page", "1").cookie(admin))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 끝난_제한에_푼_운영자가_보인다() throws Exception {
        work();
        mvc.perform(get("/api/admin/sanctions").param("status", "ENDED").cookie(admin))
                .andExpect(jsonPath("$.data.items[0].endedBy").value("LIFTED"))
                .andExpect(jsonPath("$.data.items[0].liftedByName").value("다현"));
    }

    @Test
    void 운영자가_아니면_403() throws Exception {
        mvc.perform(get("/api/admin/logs").cookie(login("jisu@x.com")))
                .andExpect(status().isForbidden());
    }
}
