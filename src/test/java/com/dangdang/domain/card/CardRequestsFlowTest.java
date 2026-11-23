package com.dangdang.domain.card;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 11/20 언니 요청: 질문 목록 · 시리즈 이름 · 슬라이드 작은 글·출처 · 정정 요약/자세히 · 출처·장 수 · 카드 공감 · 소식 칸 카드 요약
class CardRequestsFlowTest extends ReminderTestBase {

    static final String CARD = "{\"title\":\"식후 혈당, 언제 재나요?\",\"series\":\"GUIDELINE\","
            + "\"body\":[{\"eyebrow\":\"측정 시간\",\"title\":\"1장\",\"text\":\"병원에서 정해 준 시간에 잽니다.\",\"source\":\"대한당뇨병학회\"},"
            + "{\"title\":\"2장\",\"text\":\"두 번째 장\"}],"
            + "\"sources\":[{\"title\":\"출처 1\",\"url\":\"https://a.example\"},{\"title\":\"출처 2\",\"url\":\"https://b.example\"}],"
            + "\"questions\":[\"제 측정 시간이 1시간인지 다시 확인하고 싶어요\"],"
            + "\"checkedOn\":\"2026-11-09\"}";

    Cookie jisu;
    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
    }

    @AfterEach
    void cleanCards() {
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM content_cards");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int publishedCard(String json) throws Exception {
        String res = postJson(admin, "/api/admin/cards", json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(res, "$.data.id");
        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());
        return id;
    }

    @Test
    void 상세에_시리즈_이름_질문_슬라이드_작은_글과_출처가_온다() throws Exception {
        int id = publishedCard(CARD);

        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.seriesLabel").value("지침은 이렇게 말해요"))
                .andExpect(jsonPath("$.data.questions[0]").value("제 측정 시간이 1시간인지 다시 확인하고 싶어요"))
                .andExpect(jsonPath("$.data.slides[0].eyebrow").value("측정 시간"))
                .andExpect(jsonPath("$.data.slides[0].source").value("대한당뇨병학회"))
                .andExpect(jsonPath("$.data.slides[1].eyebrow").doesNotExist())
                .andExpect(jsonPath("$.data.reacted").value(false));
    }

    @Test
    void 질문을_안_보내면_빈_목록() throws Exception {
        int id = publishedCard(CARD.replace("\"questions\":[\"제 측정 시간이 1시간인지 다시 확인하고 싶어요\"],", ""));

        mvc.perform(get("/api/cards/" + id).cookie(jisu)).andExpect(jsonPath("$.data.questions.length()").value(0));
    }

    @Test
    void 목록과_저장_목록에_출처_수와_장_수() throws Exception {
        int id = publishedCard(CARD);
        mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu)).andExpect(status().isOk());

        mvc.perform(get("/api/cards").cookie(jisu))
                .andExpect(jsonPath("$.data[0].seriesLabel").value("지침은 이렇게 말해요"))
                .andExpect(jsonPath("$.data[0].sourceCount").value(2))
                .andExpect(jsonPath("$.data[0].slideCount").value(2));
        mvc.perform(get("/api/me/card-saves").cookie(jisu))
                .andExpect(jsonPath("$.data[0].sourceCount").value(2))
                .andExpect(jsonPath("$.data[0].slideCount").value(2));
    }

    @Test
    void 정정은_요약_한_줄과_자세한_내용이_따로() throws Exception {
        int id = publishedCard(CARD);

        postJson(admin, "/api/admin/cards/" + id + "/correct", "{\"note\":\"요약 없이\"}")
                .andExpect(status().isBadRequest());
        postJson(admin, "/api/admin/cards/" + id + "/correct",
                "{\"summary\":\"식후 측정 시간을 고쳤어요\",\"note\":\"2장 문장을 학회 원문대로 고쳤어요.\"}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.status").value("CORRECTED"))
                .andExpect(jsonPath("$.data.correctionSummary").value("식후 측정 시간을 고쳤어요"))
                .andExpect(jsonPath("$.data.correctionNote").value("2장 문장을 학회 원문대로 고쳤어요."));
    }

    @Test
    void 공감은_눌렀는지만_알려_주고_두_번_눌러도_한_번() throws Exception {
        int id = publishedCard(CARD);

        mvc.perform(put("/api/cards/" + id + "/reaction").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reacted").value(true))
                .andExpect(jsonPath("$.data.reactionCount").doesNotExist());   // 숫자는 안 보여 준다 (COM-12)
        mvc.perform(put("/api/cards/" + id + "/reaction").cookie(jisu)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT count(*) FROM card_reactions", Integer.class)).isEqualTo(1);

        mvc.perform(delete("/api/cards/" + id + "/reaction").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(delete("/api/cards/" + id + "/reaction").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/cards/" + id).cookie(jisu)).andExpect(jsonPath("$.data.reacted").value(false));
    }

    @Test
    void 내려간_카드에는_공감할_수_없다() throws Exception {
        int id = publishedCard(CARD);
        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());

        mvc.perform(put("/api/cards/" + id + "/reaction").cookie(jisu))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CARD_40903"));
    }

    @Test
    void 소식_칸에_연결된_카드의_장_수_출처_수_확인일() throws Exception {
        int id = publishedCard(CARD);
        postJson(admin, "/api/admin/announcements",
                "{\"slot\":\"COMMUNITY\",\"kind\":\"NEWS\",\"title\":\"새 카드\",\"contentCardId\":" + id + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.card.slideCount").value(2));
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"카드 없음\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/announcements").param("slot", "COMMUNITY").cookie(jisu))
                .andExpect(jsonPath("$.data[0].card.slideCount").value(2))
                .andExpect(jsonPath("$.data[0].card.sourceCount").value(2))
                .andExpect(jsonPath("$.data[0].card.checkedOn").value("2026-11-09"));
        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(jsonPath("$.data[0].card").value(org.hamcrest.Matchers.nullValue()));
    }
}
