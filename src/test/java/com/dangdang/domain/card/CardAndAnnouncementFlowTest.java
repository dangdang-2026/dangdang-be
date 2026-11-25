package com.dangdang.domain.card;

import com.dangdang.domain.announcement.service.AnnouncementService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
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

    @Autowired AnnouncementService announcementService;

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
                "{\"summary\":\"1장을 고쳤어요\",\"note\":\"1장 문장을 원문대로 고쳤어요.\",\"body\":[{\"title\":\"1장\",\"text\":\"고친 문장\"}]}")
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
        postJson(admin, "/api/admin/cards/" + id + "/correct", "{\"summary\":\"1장을 고쳤어요\",\"note\":\"1장 문장을 원문대로 고쳤어요.\"}")
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

    private Integer announce(String json) throws Exception {
        String res = postJson(admin, "/api/admin/announcements", json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private Instant activeTo(Integer announcementId) {
        java.sql.Timestamp t = jdbc.queryForObject("SELECT active_to FROM announcements WHERE id = ?",
                java.sql.Timestamp.class, announcementId);
        return t == null ? null : t.toInstant();
    }

    @Test
    void 카드를_내리면_걸어_둔_소식도_끝나고_다른_카드_소식은_그대로() throws Exception {
        int id = publishedCard();
        int other = publishedCard();
        Integer open = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"카드 소식\",\"contentCardId\":" + id + "}");
        Integer later = announce("{\"slot\":\"COMMUNITY\",\"kind\":\"NEWS\",\"title\":\"내일부터\","
                + "\"contentCardId\":" + id + ",\"activeFrom\":\"2026-11-10T00:00:00Z\"}");
        Integer ended = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"벌써 끝남\",\"contentCardId\":" + id + "}");
        Instant endedAt = NOW.minus(Duration.ofHours(1));
        jdbc.update("UPDATE announcements SET active_from = ?, active_to = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.minus(Duration.ofHours(2))), java.sql.Timestamp.from(endedAt), ended);
        Integer otherCard = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"다른 카드\",\"contentCardId\":" + other + "}");

        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());

        assertThat(activeTo(open)).isEqualTo(NOW);                                         // 지금 끝남 (앱 시계)
        assertThat(activeTo(later)).isEqualTo(Instant.parse("2026-11-10T00:00:00Z"));    // 시작 전이면 끝 = 시작
        assertThat(activeTo(ended)).isEqualTo(endedAt);                                   // 이미 끝난 건 그대로
        assertThat(activeTo(otherCard)).isNull();                                         // 다른 카드 소식은 안 건드림
        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("다른 카드"));
    }

    @Test
    void 카드를_내린_뒤_DB로_다시_올려도_옛_소식은_안_뜬다() throws Exception {   // 나중의 「다시 올리기」 전에 미리 막기
        int id = publishedCard();
        announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"카드 소식\",\"contentCardId\":" + id + "}");
        mvc.perform(post("/api/admin/cards/" + id + "/withdraw").cookie(admin)).andExpect(status().isOk());

        clock.set(NOW.plusSeconds(60));
        jdbc.update("UPDATE content_cards SET status = 'PUBLISHED' WHERE id = ?", id);   // 「다시 올리기」 흉내
        mvc.perform(get("/api/announcements").param("slot", "SPLASH").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── 동시에 (잠금이 빠지면 깨진다. 시간 대신 pg_stat_activity로 잠금 대기를 확인하고 진행) ──

    @Test
    void 카드를_내리는_중에_그_카드로_소식을_걸면_내리기가_끝난_뒤_409() throws Exception {
        int id = publishedCard();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection withdrawing = jdbc.getDataSource().getConnection()) {
            // 내리기가 하는 것처럼 카드 줄을 FOR UPDATE로 잡고, 내림 표시와 소식 끝내기까지 한 채로 멈춰 둔다
            withdrawing.setAutoCommit(false);
            try (PreparedStatement lock = withdrawing.prepareStatement("SELECT id FROM content_cards WHERE id = ? FOR UPDATE");
                 PreparedStatement mark = withdrawing.prepareStatement(
                         "UPDATE content_cards SET status = 'WITHDRAWN', withdrawn_at = ?, updated_at = ? WHERE id = ?");
                 PreparedStatement endAll = withdrawing.prepareStatement(
                         "UPDATE announcements SET active_to = ? WHERE content_card_id = ? AND active_to IS NULL")) {
                lock.setLong(1, id);
                lock.executeQuery();
                mark.setTimestamp(1, Timestamp.from(NOW));
                mark.setTimestamp(2, Timestamp.from(NOW));
                mark.setLong(3, id);
                mark.executeUpdate();
                endAll.setTimestamp(1, Timestamp.from(NOW));
                endAll.setLong(2, id);
                endAll.executeUpdate();
            }

            Future<Integer> creating = pool.submit(() -> postJson(admin, "/api/admin/announcements",
                    "{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"카드 소식\",\"contentCardId\":" + id + "}")
                    .andReturn().getResponse().getStatus());
            waitUntilSomeoneWaitsForLock();   // 소식 걸기가 카드 줄(FOR SHARE)에서 기다리는 중. 안 잠그면 바로 201
            withdrawing.commit();

            assertThat(creating.get(10, TimeUnit.SECONDS)).isEqualTo(409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(openAnnouncements(id)).isZero();
    }

    @Test
    void 소식을_거는_중에_카드를_내리면_그_소식도_끝난다() throws Exception {
        int id = publishedCard();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection creating = jdbc.getDataSource().getConnection()) {
            // 소식 걸기가 하는 것처럼 카드 줄을 FOR SHARE로 잡고 열린 소식을 넣은 채로 멈춰 둔다
            creating.setAutoCommit(false);
            try (PreparedStatement lock = creating.prepareStatement("SELECT id FROM content_cards WHERE id = ? FOR SHARE");
                 PreparedStatement insert = creating.prepareStatement("INSERT INTO announcements"
                         + " (slot, kind, title, link_url, content_card_id, active_from) VALUES ('SPLASH', 'NEWS', '카드 소식', ?, ?, ?)")) {
                lock.setLong(1, id);
                lock.executeQuery();
                insert.setString(1, "/cards/" + id);
                insert.setLong(2, id);
                insert.setTimestamp(3, Timestamp.from(NOW));
                insert.executeUpdate();
            }

            // 내리기 밖에서 불러도 endAllForCard가 스스로 카드 줄을 잡는지 본다 (안 잡으면 아직 안 보이는 소식을 놓친다)
            Future<?> ending = pool.submit(() -> {
                announcementService.endAllForCard((long) id, NOW);
                return null;
            });
            waitUntilSomeoneWaitsForLock();
            creating.commit();

            ending.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(openAnnouncements(id)).isZero();
    }

    @Test
    void 카드를_내리는_중에_소식을_끝내도_내린_시각을_덮어쓰지_않는다() throws Exception {
        int id = publishedCard();
        Integer a = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"카드 소식\",\"contentCardId\":" + id + "}");
        jdbc.update("UPDATE announcements SET active_from = ? WHERE id = ?", Timestamp.from(NOW.minus(Duration.ofHours(1))), a);
        Instant withdrawnAt = NOW.minus(Duration.ofMinutes(10));   // 내리기가 먼저 시작했다
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection withdrawing = jdbc.getDataSource().getConnection()) {
            // 내리기가 카드 줄을 잡고 소식 끝 시각을 내린 시각으로 바꾼 채로 멈춰 둔다 (소식 줄도 잠긴 상태)
            withdrawing.setAutoCommit(false);
            try (PreparedStatement lock = withdrawing.prepareStatement("SELECT id FROM content_cards WHERE id = ? FOR UPDATE");
                 PreparedStatement endAll = withdrawing.prepareStatement(
                         "UPDATE announcements SET active_to = ? WHERE content_card_id = ?")) {
                lock.setLong(1, id);
                lock.executeQuery();
                endAll.setTimestamp(1, Timestamp.from(withdrawnAt));
                endAll.setLong(2, id);
                endAll.executeUpdate();
            }

            Future<Integer> ending = pool.submit(() -> mvc.perform(post("/api/admin/announcements/" + a + "/end").cookie(admin))
                    .andReturn().getResponse().getStatus());
            waitUntilSomeoneWaitsForLock();
            withdrawing.commit();

            assertThat(ending.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
        // 잠가서 읽으면 내리기가 끝낸 뒤 값(더 이른 끝)을 보고 그대로 둔다. 안 잠갔으면 NULL을 읽고 지금으로 덮어쓴다
        assertThat(activeTo(a)).isEqualTo(withdrawnAt);
    }

    private int openAnnouncements(int cardId) {
        return jdbc.queryForObject("SELECT count(*) FROM announcements WHERE content_card_id = ?"
                + " AND (active_to IS NULL OR active_to > ?)", Integer.class, cardId, Timestamp.from(NOW));
    }

    // 다른 연결이 잠금을 기다리기 시작할 때까지 (CareSiteFlowTest와 같은 방식)
    private void waitUntilSomeoneWaitsForLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity"
                    + " WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("잠금을 기다리는 연결이 없어요");
    }

    // ── 배포 전에 내린 카드 정리 (V20261125_2) ──

    @Test
    void 마이그레이션은_이미_내린_카드의_열린_소식을_내린_시각으로_끝낸다() throws Exception {
        int withdrawn = publishedCard();
        int noWithdrawnAt = publishedCard();
        int live = publishedCard();
        Integer open = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"열림\",\"contentCardId\":" + withdrawn + "}");
        Integer future = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"내일까지\",\"contentCardId\":" + withdrawn
                + ",\"activeTo\":\"2026-11-10T00:00:00Z\"}");
        Integer notStarted = announce("{\"slot\":\"COMMUNITY\",\"kind\":\"NEWS\",\"title\":\"내일부터\","
                + "\"contentCardId\":" + withdrawn + ",\"activeFrom\":\"2026-11-10T00:00:00Z\"}");
        Integer ended = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"벌써 끝남\",\"contentCardId\":" + withdrawn + "}");
        Integer old = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"내린 시각 없음\",\"contentCardId\":" + noWithdrawnAt + "}");
        Integer liveOpen = announce("{\"slot\":\"SPLASH\",\"kind\":\"NEWS\",\"title\":\"올라간 카드\",\"contentCardId\":" + live + "}");

        Instant withdrawnAt = NOW.plus(Duration.ofHours(1));
        Instant endedAt = NOW.plus(Duration.ofMinutes(30));            // 내린 시각보다 먼저 끝남
        Instant updatedAt = NOW.plus(Duration.ofHours(2));
        jdbc.update("UPDATE announcements SET active_to = ? WHERE id = ?", Timestamp.from(endedAt), ended);
        // 이 PR 전 내리기 흉내: 카드만 내려가고 소식은 열린 채
        jdbc.update("UPDATE content_cards SET status = 'WITHDRAWN', withdrawn_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(withdrawnAt), Timestamp.from(withdrawnAt), withdrawn);
        jdbc.update("UPDATE content_cards SET status = 'WITHDRAWN', withdrawn_at = NULL, updated_at = ? WHERE id = ?",
                Timestamp.from(updatedAt), noWithdrawnAt);

        jdbc.update(migrationUpdate());

        assertThat(activeTo(open)).isEqualTo(withdrawnAt);
        assertThat(activeTo(future)).isEqualTo(withdrawnAt);                              // 내린 뒤까지 열려 있던 것도
        assertThat(activeTo(notStarted)).isEqualTo(Instant.parse("2026-11-10T00:00:00Z")); // 시작 전이면 끝 = 시작
        assertThat(activeTo(ended)).isEqualTo(endedAt);                                   // 더 일찍 끝난 건 그대로
        assertThat(activeTo(old)).isEqualTo(updatedAt);                                   // withdrawn_at이 없으면 updated_at
        assertThat(activeTo(liveOpen)).isNull();                                          // 올라가 있는 카드는 안 건드림
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_announcements_card'",
                Integer.class)).isEqualTo(1);
    }

    // 마이그레이션 파일의 UPDATE 문 하나 (Flyway는 빈 표에서 이미 돌았으니 데이터를 넣고 다시 돌린다)
    private String migrationUpdate() throws Exception {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V20261125_2__announcements_end_withdrawn_cards.sql").readAllBytes(), StandardCharsets.UTF_8);
        int start = sql.indexOf("UPDATE announcements");
        return sql.substring(start, sql.indexOf(';', start));
    }
}
