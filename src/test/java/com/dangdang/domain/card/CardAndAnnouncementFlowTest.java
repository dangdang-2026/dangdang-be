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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 당당 소식 카드뉴스 (U-26~U-28) + 스플래시·커뮤니티 소식/광고 카드 (S-01b·S-01c). 고정 시계: 2026-11-09 13:00
class CardAndAnnouncementFlowTest extends ReminderTestBase {

    static final String CARD = "{\"title\":\"식후 혈당, 언제 재나요?\",\"series\":\"GUIDELINE\","
            + "\"body\":[{\"title\":\"1장\",\"text\":\"병원에서 정해 준 시간에 잽니다.\"}],"
            + "\"sources\":[{\"title\":\"시험 출처\",\"url\":\"https://example.org\"}],\"checkedOn\":\"2026-11-09\"}";

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
    void cleanCards() {   // 카드·소식은 회원과 상관없는 표라 따로 지운다
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM content_cards");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int createCard(String json) throws Exception {
        String res = postJson(admin, "/api/admin/cards", json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private int publishedCard() throws Exception {
        int id = createCard(CARD);
        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());
        return id;
    }

    // ── 카드뉴스 ──

    @Test
    void 쓰는_중인_카드는_안_보이고_올리면_보인다() throws Exception {
        int id = createCard(CARD);
        mvc.perform(get("/api/cards").cookie(jisu)).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CARD_40401"));

        mvc.perform(post("/api/admin/cards/" + id + "/publish").cookie(admin)).andExpect(status().isOk());

        mvc.perform(get("/api/cards").cookie(jisu)).andExpect(jsonPath("$.data[0].saved").value(false));
        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.slides[0].text").value("병원에서 정해 준 시간에 잽니다."))
                .andExpect(jsonPath("$.data.sources[0].url").value("https://example.org"))
                .andExpect(jsonPath("$.data.checkedOn").value("2026-11-09"));
    }

    @Test
    void 출처나_확인일이_없으면_카드를_못_만든다() throws Exception {
        postJson(admin, "/api/admin/cards", CARD.replace("\"sources\":[{\"title\":\"시험 출처\",\"url\":\"https://example.org\"}]",
                "\"sources\":[]")).andExpect(status().isBadRequest());
        postJson(admin, "/api/admin/cards", CARD.replace(",\"checkedOn\":\"2026-11-09\"", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 저장은_두_번_눌러도_한_번_취소하면_빈_목록() throws Exception {
        int id = publishedCard();
        mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu)).andExpect(jsonPath("$.data.saved").value(true));
        mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/me/card-saves").cookie(jisu)).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/cards").cookie(jisu)).andExpect(jsonPath("$.data[0].saved").value(true));

        mvc.perform(delete("/api/cards/" + id + "/save").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/me/card-saves").cookie(jisu)).andExpect(jsonPath("$.data.length()").value(0));   // U-27b
    }

    @Test
    void 정정하면_정정_문구와_고친_내용() throws Exception {
        int id = publishedCard();
        postJson(admin, "/api/admin/cards/" + id + "/correct",
                "{\"note\":\"1장 문장을 원문대로 고쳤어요.\",\"body\":[{\"title\":\"1장\",\"text\":\"고친 문장\"}]}")
                .andExpect(status().isOk());

        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.status").value("CORRECTED"))
                .andExpect(jsonPath("$.data.correctionNote").value("1장 문장을 원문대로 고쳤어요."))
                .andExpect(jsonPath("$.data.slides[0].text").value("고친 문장"));
    }

    @Test
    void 내리면_목록에서_빠지고_저장한_사람에겐_내용_없이_남는다() throws Exception {
        int id = publishedCard();
        mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu));
        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());

        mvc.perform(get("/api/cards").cookie(jisu)).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/me/card-saves").cookie(jisu))
                .andExpect(jsonPath("$.data[0].status").value("WITHDRAWN"));
        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.slides.length()").value(0))
                .andExpect(jsonPath("$.data.withdrawnAt").exists());
        mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CARD_40901"));
        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin))
                .andExpect(jsonPath("$.errorCode").value("CARD_40902"));
    }

    @Test
    void 일반_사용자는_카드를_못_만든다() throws Exception {
        postJson(jisu, "/api/admin/cards", CARD).andExpect(status().isForbidden());
    }

    @Test
    void 저장을_동시에_두_번_눌러도_한_줄() throws Exception {   // 「있나 확인 → save」였으면 한쪽이 UNIQUE에 걸려 500
        int id = publishedCard();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_saves WHERE card_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void 검수자를_적으면_상세에_보이고_운영자_목록엔_쓰는_중_카드도_보인다() throws Exception {
        int draft = createCard(CARD);
        mvc.perform(get("/api/admin/cards").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(draft))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"));

        int id = publishedCard();
        postJson(admin, "/api/admin/cards/" + id + "/review", "{\"reviewer\":\"다현\",\"reviewedOn\":\"2026-11-09\"}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.reviewer").value("다현"))
                .andExpect(jsonPath("$.data.reviewedOn").value("2026-11-09"));
        postJson(admin, "/api/admin/cards/" + id + "/review", "{\"reviewer\":\"다현\"}")   // 검수일 없음
                .andExpect(status().isBadRequest());
    }

    // ── 스플래시·커뮤니티 소식/광고 ──

    @Test
    void 지금_걸린_것만_소식이_먼저() throws Exception {
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"SPLASH\",\"kind\":\"AD\",\"title\":\"제휴 광고\","
                + "\"linkUrl\":\"https://partner.example\",\"reviewedBy\":\"다현\"}").andExpect(status().isCreated());
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"당당 소식\","
                + "\"linkUrl\":\"/cards/1\"}").andExpect(status().isCreated());
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"내일부터\","
                + "\"activeFrom\":\"2026-11-10T00:00:00Z\"}");
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"COMMUNITY\",\"kind\":\"NEWS\",\"title\":\"커뮤니티 칸\"}");

        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", contains("당당 소식", "제휴 광고")));

        clock.set(NOW.plus(Duration.ofDays(1)));
        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void 광고는_심사한_사람이_있어야_하고_내리면_안_보인다() throws Exception {
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"COMMUNITY\",\"kind\":\"AD\",\"title\":\"광고\"}")
                .andExpect(status().isBadRequest());
        String res = postJson(admin, "/api/admin/announcements",
                "{\"slot\":\"COMMUNITY\",\"kind\":\"AD\",\"title\":\"광고\",\"reviewedBy\":\"다현\"}")
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(res, "$.data.id");

        mvc.perform(post("/api/admin/announcements/" + id + "/end").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/announcements").param("slot", "COMMUNITY").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 내린_카드나_쓰는_중인_카드는_소식으로_안_걸린다() throws Exception {
        int draft = createCard(CARD);
        postJson(admin, "/api/admin/announcements",
                "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"쓰는 중\",\"contentCardId\":" + draft + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ANNOUNCEMENT_40901"));

        int id = publishedCard();
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"카드 소식\","
                + "\"linkUrl\":\"/cards/" + id + "\",\"contentCardId\":" + id + "}").andExpect(status().isCreated());
        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(0));   // 내린 카드를 스플래시에 띄우지 않는다
    }

    @Test
    void 없는_소식은_404_이상한_링크는_400() throws Exception {
        mvc.perform(post("/api/admin/announcements/999999/end").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ANNOUNCEMENT_40401"));
        postJson(admin, "/api/admin/announcements",
                "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"링크\",\"linkUrl\":\"javascript:alert(1)\"}")
                .andExpect(status().isBadRequest());
        postJson(admin, "/api/admin/announcements",
                "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"링크\",\"linkUrl\":\"//evil.example\"}")
                .andExpect(status().isBadRequest());
        postJson(admin, "/api/admin/announcements",       // 브라우저는 /\ 를 // 처럼 읽어 외부로 보낸다
                "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"링크\",\"linkUrl\":\"/\\\\evil.example\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 카드를_건_소식은_링크를_서버가_정하고_커뮤니티는_한_칸만() throws Exception {
        int id = publishedCard();
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"COMMUNITY\",\"kind\":\"NEWS\",\"title\":\"카드 소식\","
                + "\"linkUrl\":\"/cards/999\",\"contentCardId\":" + id + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.linkUrl").value("/cards/" + id));
        postJson(admin, "/api/admin/announcements", "{\"slot\":\"COMMUNITY\",\"kind\":\"AD\",\"title\":\"광고\","
                + "\"reviewedBy\":\"다현\"}").andExpect(status().isCreated());

        mvc.perform(get("/api/announcements").param("slot", "COMMUNITY").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("카드 소식"));
    }

    @Test
    void 시작_전_소식을_내리면_끝이_시작보다_앞서지_않는다() throws Exception {
        String res = postJson(admin, "/api/admin/announcements", "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"내일부터\","
                + "\"activeFrom\":\"2026-11-10T00:00:00Z\"}").andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(res, "$.data.id");

        mvc.perform(post("/api/admin/announcements/" + id + "/end").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeTo").value("2026-11-10T00:00:00Z"));
    }

    @Test
    void 내린_카드는_정정_문구도_안_보이고_저장_취소는_동시에_두_번_해도_200() throws Exception {
        int id = publishedCard();
        mvc.perform(put("/api/cards/" + id + "/save").cookie(jisu)).andExpect(status().isOk());
        postJson(admin, "/api/admin/cards/" + id + "/correct", "{\"note\":\"1장 문장을 원문대로 고쳤어요.\"}")
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/cards/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.correctionNote").doesNotExist());

        ExecutorService pool = Executors.newFixedThreadPool(2);   // 읽고 지웠으면 두 번째가 0줄 삭제로 500
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(delete("/api/cards/" + id + "/save").cookie(jisu)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) {
                assertThat(f.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_saves WHERE card_id = ?", Integer.class, id)).isZero();
    }
}
