package com.dangdang.domain.community;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ⑩ PR ①: 익명 글쓴이 안 보기(A) · 댓글 글쓴이·익명 번호(B) · 주수 배지(C) · Best(D) · 닉네임(I) · 검색 인기순(K)
// 시계는 ReminderTestBase 고정 (2026-11-09 13:00 KST)이라 주수가 매번 같다
class CommunityAnonymityFlowTest extends ReminderTestBase {

    Cookie jisu;    // 「말랑한 두부」, 주수 배지 켬
    Cookie minji;   // 「포근한 고구마」
    Cookie hana;    // 「새콤한 자두」
    Long hanaId;

    @BeforeEach
    void setUp() throws Exception {
        hanaId = userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"),
                clock.instant())).getId();
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        hana = login("hana@x.com");
        profile(jisu, "말랑한 두부", true);
        profile(minji, "포근한 고구마", false);
        profile(hana, "새콤한 자두", false);
    }

    @AfterEach
    void cleanPosts() { // posts는 프로필이 지워져도 남는다(SET NULL) → 먼저 지운다 (댓글·공감은 CASCADE)
        jdbc.update("DELETE FROM posts");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void profile(Cookie who, String nickname, boolean badge) throws Exception {
        postJson(who, "/api/community/profiles", "{\"nickname\":\"" + nickname + "\",\"showWeekBadge\":" + badge + "}")
                .andExpect(status().isCreated());
    }

    private String write(Cookie who, boolean anonymous) throws Exception {
        String res = postJson(who, "/api/posts", "{\"category\":\"MEASUREMENT\",\"title\":\"제목\",\"content\":\"본문\","
                + "\"isAnonymous\":" + anonymous + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private String comment(Cookie who, String postId, boolean anonymous) throws Exception {
        String res = postJson(who, "/api/posts/" + postId + "/comments",
                "{\"content\":\"댓글\",\"isAnonymous\":" + anonymous + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private void react(Cookie who, String commentId) throws Exception {
        postJson(who, "/api/comments/" + commentId + "/reactions", "{}").andExpect(status().isOk());
    }

    private String myProfileId(Cookie who) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/community/profiles/me").cookie(who))
                .andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private void pregnancy(Long userId, String state, LocalDate dueDate, LocalDate deliveryDate) {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, delivery_date, state) VALUES (?, ?, ?, ?)",
                userId, dueDate, deliveryDate, state);
    }

    // ── A ──
    @Test
    void 익명_글로_안_보기_하면_그_사람_익명_글만_안_보이고_이름_글은_보이며_목록엔_익명_사용자() throws Exception {
        String anon = write(jisu, true);
        String anon2 = write(jisu, true);
        String named = write(jisu, false);
        postJson(minji, "/api/blocks", "{\"postId\":\"" + anon + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true));

        mvc.perform(get("/api/posts/" + anon2).cookie(minji)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + named).cookie(minji)).andExpect(status().isOk());   // 이름 글은 그대로 → 정체를 짐작 못 함
        mvc.perform(get("/api/posts").cookie(minji))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(named));
        mvc.perform(get("/api/blocks").cookie(minji))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("익명 사용자"))
                .andExpect(jsonPath("$.data.items[0].anonymous").value(true))
                .andExpect(jsonPath("$.data.items[0].profileId").value((Object) null))
                .andExpect(content().string(not(containsString("말랑한 두부"))));
    }

    @Test
    void 이름으로_막으면_이름_글_댓글만_가리고_익명_글_댓글은_보이며_둘_다_막으면_다_안_보인다() throws Exception {
        String anon = write(jisu, true);
        String named = write(jisu, false);
        String minjiPost = write(minji, false);
        String anonComment = comment(jisu, minjiPost, true);
        comment(jisu, minjiPost, false);
        postJson(minji, "/api/blocks", "{\"blockedProfileId\":\"" + myProfileId(jisu) + "\"}").andExpect(status().isOk());

        mvc.perform(get("/api/posts/" + named).cookie(minji)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + anon).cookie(minji)).andExpect(status().isOk());
        mvc.perform(get("/api/posts").param("q", "제목").cookie(minji))          // 검색
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(minjiPost))
                .andExpect(jsonPath("$.data.items[1].id").value(anon));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(minji))   // 인기
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/posts/" + minjiPost + "/comments").cookie(minji))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(anonComment));
        mvc.perform(get("/api/posts/" + minjiPost).cookie(minji))
                .andExpect(jsonPath("$.data.commentCount").value(1));

        postJson(minji, "/api/blocks", "{\"postId\":\"" + anon + "\"}").andExpect(status().isOk());   // 익명도 막기
        mvc.perform(get("/api/posts/" + anon).cookie(minji)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts").cookie(minji))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(minjiPost));
        mvc.perform(get("/api/posts/" + minjiPost + "/comments").cookie(minji))
                .andExpect(jsonPath("$.data.totalCount").value(0));
        postJson(minji, "/api/comments/" + anonComment + "/reactions", "{}")      // 안 보이는 댓글엔 공감도 못 한다
                .andExpect(status().isNotFound());
    }

    @Test
    void 익명으로_막은_줄은_blockId로만_풀고_남의_줄은_못_푼다() throws Exception {
        String anon = write(jisu, true);
        postJson(minji, "/api/blocks", "{\"postId\":\"" + anon + "\"}").andExpect(status().isOk());
        String blockId = JsonPath.read(mvc.perform(get("/api/blocks").cookie(minji))
                .andReturn().getResponse().getContentAsString(), "$.data.items[0].blockId");

        mvc.perform(delete("/api/blocks/" + myProfileId(jisu)).cookie(minji)).andExpect(status().isOk()); // 이름 줄만 푼다
        mvc.perform(delete("/api/blocks/entries/" + blockId).cookie(hana)).andExpect(status().isOk());    // 남의 줄 — 그대로
        mvc.perform(get("/api/posts/" + anon).cookie(minji)).andExpect(status().isNotFound());

        mvc.perform(delete("/api/blocks/entries/" + blockId).cookie(minji))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false));
        mvc.perform(get("/api/posts/" + anon).cookie(minji)).andExpect(status().isOk());
    }

    @Test
    void 익명_댓글로도_막고_대상은_하나만_없는_대상은_404_내_익명_글은_400() throws Exception {
        String postId = write(minji, false);
        String c = comment(jisu, postId, true);
        postJson(hana, "/api/blocks", "{\"commentId\":\"" + c + "\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT via_anonymous FROM profile_blocks", Boolean.class)).isTrue();

        postJson(hana, "/api/blocks", "{\"postId\":\"" + postId + "\",\"commentId\":\"" + c + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        postJson(hana, "/api/blocks", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        postJson(hana, "/api/blocks", "{\"postId\":\"999999\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40402"));

        mvc.perform(delete("/api/comments/" + c).cookie(jisu)).andExpect(status().isOk());
        postJson(minji, "/api/blocks", "{\"commentId\":\"" + c + "\"}")     // 지운 댓글은 없는 대상
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40402"));

        String mine = write(jisu, true);
        postJson(jisu, "/api/blocks", "{\"postId\":\"" + mine + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40003"));
    }

    @Test
    void 같은_사람을_이름으로_한_번_익명으로_한_번_막으면_두_줄이고_다시_해도_그대로() throws Exception {
        String anon = write(jisu, true);
        postJson(minji, "/api/blocks", "{\"blockedProfileId\":\"" + myProfileId(jisu) + "\"}").andExpect(status().isOk());
        postJson(minji, "/api/blocks", "{\"postId\":\"" + anon + "\"}").andExpect(status().isOk());
        postJson(minji, "/api/blocks", "{\"postId\":\"" + anon + "\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_blocks", Long.class)).isEqualTo(2L);
        mvc.perform(get("/api/me/community-activity/counts").cookie(minji))   // 사람 수가 아니라 줄 수
                .andExpect(jsonPath("$.data.blockedCount").value(2));
    }

    @Test
    void 가려진_글_댓글과_가려진_글의_댓글로는_막을_수_없다() throws Exception {
        String hiddenPost = write(jisu, true);
        String postId = write(minji, false);
        String hiddenComment = comment(jisu, postId, true);
        String onHiddenPost = comment(hana, hiddenPost, true);
        jdbc.update("UPDATE posts SET hidden_reason = 'MEDICATION' WHERE id = ?", Long.valueOf(hiddenPost));
        jdbc.update("UPDATE comments SET hidden_reason = 'MEDICATION' WHERE id = ?", Long.valueOf(hiddenComment));

        for (String body : List.of("{\"postId\":\"" + hiddenPost + "\"}", "{\"commentId\":\"" + hiddenComment + "\"}",
                "{\"commentId\":\"" + onHiddenPost + "\"}")) {
            postJson(minji, "/api/blocks", body)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40402"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_blocks", Long.class)).isZero();
    }

    // ── B ──
    @Test
    void 익명_글의_글쓴이가_단_댓글은_이름을_골라도_익명이고_글쓴이_라벨() throws Exception {
        String postId = write(jisu, true);
        comment(jisu, postId, false);   // 실수로 이름으로 달아도

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(minji))
                .andExpect(jsonPath("$.data.items[0].author.displayName").value("익명 산모"))
                .andExpect(jsonPath("$.data.items[0].author.profileId").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].isPostAuthor").value(true))
                .andExpect(jsonPath("$.data.items[0].anonymousNo").value((Object) null))
                .andExpect(content().string(not(containsString("말랑한 두부"))));
    }

    @Test
    void 예전에_익명_글쓴이가_이름으로_단_댓글은_글쓴이_라벨이_안_붙고_마이그레이션_뒤엔_익명_글쓴이_댓글() throws Exception {
        String postId = write(jisu, true);
        // 이번 PR 전에 쌓인 모양: 익명 글의 글쓴이가 자기 글에 이름으로 단 댓글
        jdbc.update("""
                INSERT INTO comments (post_id, profile_id, content, is_anonymous, created_at, updated_at)
                SELECT ?, profile_id, '예전 댓글', false, now(), now() FROM posts WHERE id = ?
                """, Long.valueOf(postId), Long.valueOf(postId));

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(minji))   // 라벨이 닉네임과 같이 나가면 익명 글쓴이가 드러난다
                .andExpect(jsonPath("$.data.items[0].isPostAuthor").value(false));

        jdbc.update(migrationStatement("SET is_anonymous = true"));   // V20261124_95의 예전 댓글 고치기 그대로
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(minji))
                .andExpect(jsonPath("$.data.items[0].author.displayName").value("익명 산모"))
                .andExpect(jsonPath("$.data.items[0].author.profileId").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].isPostAuthor").value(true))
                .andExpect(jsonPath("$.data.items[0].anonymousNo").value((Object) null))
                .andExpect(content().string(not(containsString("말랑한 두부"))));
    }

    // 마이그레이션 파일에서 marker가 든 UPDATE 문 하나를 꺼낸다
    private String migrationStatement(String marker) throws Exception {
        String sql = new String(getClass().getResourceAsStream("/db/migration/V20261124_95__community_anonymity.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int at = sql.indexOf(marker);
        int start = sql.lastIndexOf("UPDATE", at);
        return sql.substring(start, sql.indexOf(';', at));
    }

    @Test
    void 익명_번호는_사람마다_같고_지우거나_탈퇴해도_안_밀리며_이름_글의_글쓴이_익명_댓글엔_라벨_대신_번호() throws Exception {
        String postId = write(jisu, false);
        String first = comment(minji, postId, true);    // 민지 1
        comment(hana, postId, true);                    // 하나 2
        comment(minji, postId, true);                   // 민지 다시 1
        mvc.perform(delete("/api/comments/" + first).cookie(minji)).andExpect(status().isOk());

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].anonymousNo").value(2))
                .andExpect(jsonPath("$.data.items[1].anonymousNo").value(1))   // 첫 댓글을 지웠어도 1
                .andExpect(jsonPath("$.data.items[0].isPostAuthor").value(false));

        jdbc.update("DELETE FROM users WHERE id = ?", hanaId);   // 하나 탈퇴 → 댓글 profile_id NULL
        comment(jisu, postId, true);   // 이름 글의 글쓴이가 익명으로 → 「글쓴이」를 붙이면 드러나니 번호 3 (2를 다시 쓰지 않는다)

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(minji))
                .andExpect(jsonPath("$.data.items[0].author.displayName").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.data.items[0].anonymousNo").value((Object) null))
                .andExpect(jsonPath("$.data.items[1].anonymousNo").value(1))
                .andExpect(jsonPath("$.data.items[2].anonymousNo").value(3))
                .andExpect(jsonPath("$.data.items[2].isPostAuthor").value(false))
                .andExpect(jsonPath("$.data.items[2].author.profileId").value((Object) null));
    }

    @Test
    void 두_사람이_동시에_첫_익명_댓글을_달아도_번호가_겹치지_않는다() throws Exception {
        String postId = write(jisu, false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (Cookie who : List.of(minji, hana)) {
                Callable<String> task = () -> {
                    start.await();
                    return comment(who, postId, true);
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            for (Future<String> f : results) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
        List<Integer> numbers = jdbc.queryForList(
                "SELECT anonymous_no FROM comments WHERE post_id = ? ORDER BY anonymous_no", Integer.class,
                Long.valueOf(postId));
        assertThat(numbers).containsExactly(1, 2);
    }

    // ── C ──
    @Test
    void 배지를_켠_사람은_임신_주수_내림이고_익명_글과_다른_상황엔_없다() throws Exception {
        // 11/9 기준 예정일 2027-02-05 → 280 - 88 = 192일 = 27주 3일 → 「임신 27주」 (내림)
        pregnancy(jisuId, "PREGNANT", LocalDate.of(2027, 2, 5), null);
        pregnancy(minjiId, "PREGNANT", LocalDate.of(2027, 2, 5), null);   // 민지는 배지를 껐다
        String named = write(jisu, false);
        String anon = write(jisu, true);
        String minjiPost = write(minji, false);
        comment(jisu, minjiPost, false);

        mvc.perform(get("/api/posts/" + named).cookie(minji))
                .andExpect(jsonPath("$.data.author.weekBadge").value("임신 27주"));
        mvc.perform(get("/api/posts").cookie(minji))   // 최신순: [0]=민지 글, [1]=익명 글, [2]=이름 글
                .andExpect(jsonPath("$.data.items[0].author.weekBadge").value((Object) null))
                .andExpect(jsonPath("$.data.items[1].author.weekBadge").value((Object) null))
                .andExpect(jsonPath("$.data.items[2].author.weekBadge").value("임신 27주"));
        mvc.perform(get("/api/posts/" + anon).cookie(minji))
                .andExpect(jsonPath("$.data.author.weekBadge").value((Object) null));
        mvc.perform(get("/api/posts/" + minjiPost + "/comments").cookie(hana))
                .andExpect(jsonPath("$.data.items[0].author.weekBadge").value("임신 27주"));

        jdbc.update("UPDATE pregnancies SET state = 'PAUSED' WHERE user_id = ?", jisuId);   // 「다른 상황」
        mvc.perform(get("/api/posts/" + named).cookie(minji))
                .andExpect(jsonPath("$.data.author.weekBadge").value((Object) null));

        jdbc.update("UPDATE pregnancies SET state = 'POSTPARTUM', delivery_date = '2026-08-01' WHERE user_id = ?", jisuId);
        mvc.perform(get("/api/posts/" + named).cookie(minji))
                .andExpect(jsonPath("$.data.author.weekBadge").value("산후 3개월"));

        jdbc.update("UPDATE pregnancies SET state = 'ENDED' WHERE user_id = ?", jisuId);
        mvc.perform(get("/api/posts/" + named).cookie(minji))
                .andExpect(jsonPath("$.data.author.weekBadge").value((Object) null));
    }

    // ── D ──
    @Test
    void 공감_3개_이상_가장_많은_댓글이_Best이고_인기_목록_카드에도_붙는다() throws Exception {
        String postId = write(jisu, false);
        String a = comment(minji, postId, true);
        String b = comment(hana, postId, false);
        for (Cookie who : List.of(jisu, minji, hana)) {
            react(who, a);
        }
        react(jisu, b);

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].isBest").value(true))
                .andExpect(jsonPath("$.data.items[1].isBest").value(false));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].bestComment.id").value(a))
                .andExpect(jsonPath("$.data.items[0].bestComment.reactionCount").value(3))
                .andExpect(jsonPath("$.data.items[0].bestComment.author.displayName").value("익명 산모"))
                .andExpect(jsonPath("$.data.items[0].bestComment.author.profileId").value((Object) null));
        mvc.perform(get("/api/posts").cookie(jisu))   // 최신 목록에는 안 붙는다
                .andExpect(jsonPath("$.data.items[0].bestComment").value((Object) null));
    }

    @Test
    void Best는_같으면_먼저_단_댓글이고_안_보기_한_사람과_지운_댓글은_빠진다() throws Exception {
        String postId = write(jisu, false);
        String a = comment(minji, postId, false);
        String b = comment(hana, postId, false);
        for (Cookie who : List.of(jisu, minji, hana)) {
            react(who, a);
            react(who, b);
        }
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))   // 3:3 → 먼저 단 a
                .andExpect(jsonPath("$.data.items[0].isBest").value(true))
                .andExpect(jsonPath("$.data.items[1].isBest").value(false));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].bestComment.id").value(a));

        postJson(jisu, "/api/blocks", "{\"commentId\":\"" + a + "\"}").andExpect(status().isOk());   // 민지 안 보기
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(b))
                .andExpect(jsonPath("$.data.items[0].isBest").value(true));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].bestComment.id").value(b));

        mvc.perform(delete("/api/comments/" + b).cookie(hana)).andExpect(status().isOk());
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].bestComment").value((Object) null));
    }

    @Test
    void 이름으로_막은_사람의_익명_댓글은_Best에_남고_익명으로_막으면_빠진다() throws Exception {
        String postId = write(jisu, false);
        String a = comment(minji, postId, true);
        for (Cookie who : List.of(jisu, minji, hana)) {
            react(who, a);
        }
        postJson(jisu, "/api/blocks", "{\"blockedProfileId\":\"" + myProfileId(minji) + "\"}").andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].isBest").value(true));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].bestComment.id").value(a));

        postJson(jisu, "/api/blocks", "{\"commentId\":\"" + a + "\"}").andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].bestComment").value((Object) null));
    }

    // ── I ──
    @Test
    void 닉네임_띄어쓰기는_한_칸만() throws Exception {
        mvc.perform(get("/api/community/profiles/nickname-availability").param("nickname", "말랑한  자두").cookie(minji))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40001"));
        mvc.perform(get("/api/community/profiles/nickname-availability").param("nickname", "말랑한 자두").cookie(minji))
                .andExpect(status().isOk());
    }

    // ── K ──
    @Test
    void 검색_인기순은_ALL이면_오래된_글도_나온다() throws Exception {
        write(jisu, false);
        clock.set(NOW.plusSeconds(3 * 24 * 3600));   // 사흘 뒤
        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("q", "제목").cookie(minji))
                .andExpect(jsonPath("$.data.items").isEmpty());          // 기본 24시간
        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("period", "ALL").param("q", "제목").cookie(minji))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("period", "ALL").cookie(minji))   // 검색어 없이 전체는 안 된다
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }
}
