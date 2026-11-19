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

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 인기 게시판 (U-23~U-25) + 이용 제한 (U-17). 고정 시계: 2026-11-09 13:00 (한국)
class PopularAndRestrictionFlowTest extends ReminderTestBase {

    Cookie jisu;     // 임신 중
    Cookie minji;    // 출산 후
    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        profile(jisu, "말랑한 두부");
        profile(minji, "포근한 고구마");
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-12-20', 'PREGNANT', now(), now())", jisuId);
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, delivery_date, state, created_at, updated_at)"
                + " VALUES (?, '2026-11-01', '2026-11-01', 'POSTPARTUM', now(), now())", minjiId);
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");                      // 역할은 로그인할 때 토큰에 들어간다
    }

    @AfterEach
    void cleanPosts() {
        jdbc.update("DELETE FROM posts");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void profile(Cookie who, String nickname) throws Exception {
        postJson(who, "/api/community/profiles", "{\"nickname\":\"" + nickname + "\"}").andExpect(status().isCreated());
    }

    private String write(Cookie who, String category, String title) throws Exception {
        String res = postJson(who, "/api/posts", "{\"category\":\"" + category + "\",\"title\":\"" + title
                + "\",\"content\":\"본문\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private void react(Cookie who, String postId) throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(who));
    }

    private void comment(Cookie who, String postId) throws Exception {
        postJson(who, "/api/posts/" + postId + "/comments", "{\"content\":\"댓글\"}").andExpect(status().isCreated());
    }

    // ── 인기 게시판 ──

    @Test
    void 인기는_공감과_댓글이_많은_순_같으면_최신() throws Exception {
        String a = write(jisu, "MIND", "공감 2");
        clock.set(NOW.plusSeconds(60));
        String b = write(jisu, "MIND", "공감 2 + 댓글 1");
        clock.set(NOW.plusSeconds(120));
        String c = write(minji, "MIND", "아무것도 없음");
        clock.set(NOW.plusSeconds(180));
        String d = write(minji, "MIND", "공감 2 (더 최신)");
        react(jisu, a); react(minji, a);
        react(jisu, b); react(minji, b); comment(minji, b);
        react(jisu, d); react(minji, d);

        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", contains("공감 2 + 댓글 1", "공감 2 (더 최신)", "공감 2", "아무것도 없음")))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 실시간은_24시간_이번_주는_7일() throws Exception {
        clock.set(NOW.minus(Duration.ofDays(2)));
        write(jisu, "MIND", "이틀 전");
        clock.set(NOW);
        write(jisu, "MIND", "방금");

        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("방금")));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("period", "WEEK").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void 식단_진료_후기는_인기_400_전체_인기에서도_빠진다() throws Exception {
        write(jisu, "MEAL", "식단 글");
        write(jisu, "MIND", "마음 글");

        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("category", "MEAL").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40002"));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("마음 글")));
    }

    @Test
    void 대상은_글쓴이의_지금_임신_상태로() throws Exception {
        write(jisu, "MIND", "임신 중 글");
        write(minji, "MIND", "출산 후 글");

        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("audience", "PREGNANT").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("임신 중 글")));
        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("audience", "POSTPARTUM").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("출산 후 글")));
        mvc.perform(get("/api/posts").param("audience", "POSTPARTUM").cookie(jisu))    // 최신순에도 된다
                .andExpect(jsonPath("$.data.items[*].title", contains("출산 후 글")));
    }

    @Test
    void 인기는_size를_안_보내도_50개까지() throws Exception {
        for (int i = 0; i < 25; i++) {
            write(jisu, "MIND", "글 " + i);
        }
        mvc.perform(get("/api/posts").param("sort", "POPULAR").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(25))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 당당_소식도_인기_400_모르는_기간은_최신순에서도_400() throws Exception {
        mvc.perform(get("/api/posts").param("sort", "POPULAR").param("category", "NEWS").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40002"));
        mvc.perform(get("/api/posts").param("period", "FOO").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 첫째를_졸업하고_둘째_임신_중이면_임신_중에만_나온다() throws Exception {
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, delivery_date, state, created_at, updated_at)"
                + " VALUES (?, '2025-01-10', '2025-01-10', 'ENDED', now(), now())", jisuId);
        write(jisu, "MIND", "둘째 임신 중 글");

        mvc.perform(get("/api/posts").param("audience", "PREGNANT").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("둘째 임신 중 글")));
        mvc.perform(get("/api/posts").param("audience", "POSTPARTUM").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // ── 이용 제한 ──

    @Test
    void 제한을_걸면_글과_댓글이_막히고_기간이_지나면_풀린다() throws Exception {
        String herPost = write(minji, "MIND", "민지 글");
        postJson(admin, "/api/admin/sanctions", "{\"userId\":" + jisuId + ",\"reason\":\"MEDICAL_ADVICE\",\"days\":7}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endsAt").value("2026-11-16T04:00:00Z"));

        postJson(jisu, "/api/posts", "{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"본문\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40301"));
        postJson(jisu, "/api/posts/" + herPost + "/comments", "{\"content\":\"댓글\"}")
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/posts").cookie(jisu)).andExpect(status().isOk());          // 읽기는 된다
        mvc.perform(get("/api/community/restriction").cookie(jisu))
                .andExpect(jsonPath("$.data.restricted").value(true))
                .andExpect(jsonPath("$.data.reason").value("MEDICAL_ADVICE"))
                .andExpect(jsonPath("$.data.endsAt").value("2026-11-16T04:00:00Z"));

        clock.set(NOW.plus(Duration.ofDays(7)));                                        // 딱 7일 뒤
        postJson(jisu, "/api/posts", "{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"본문\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/community/restriction").cookie(jisu))
                .andExpect(jsonPath("$.data.restricted").value(false));
    }

    @Test
    void 제한_중엔_내_글과_댓글_고치기도_막힌다() throws Exception {
        String myPost = write(jisu, "MIND", "내 글");
        String res = postJson(jisu, "/api/posts/" + myPost + "/comments", "{\"content\":\"내 댓글\"}")
                .andReturn().getResponse().getContentAsString();
        String myComment = JsonPath.read(res, "$.data.id");
        postJson(admin, "/api/admin/sanctions", "{\"userId\":" + jisuId + ",\"reason\":\"MEDICAL_ADVICE\",\"days\":7}")
                .andExpect(status().isCreated());

        mvc.perform(patch("/api/posts/" + myPost).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"바꾼 본문\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40301"));
        mvc.perform(patch("/api/comments/" + myComment).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"바꾼 댓글\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40301"));
    }

    @Test
    void 기간_없는_제한은_운영자가_풀어야_풀린다() throws Exception {
        String res = postJson(admin, "/api/admin/sanctions", "{\"userId\":" + jisuId + ",\"reason\":\"ABUSE\"}")
                .andExpect(jsonPath("$.data.endsAt").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        Integer sanctionId = JsonPath.read(res, "$.data.id");

        clock.set(NOW.plus(Duration.ofDays(400)));
        mvc.perform(get("/api/community/restriction").cookie(jisu)).andExpect(jsonPath("$.data.restricted").value(true));

        mvc.perform(post("/api/admin/sanctions/" + sanctionId + "/lift").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/community/restriction").cookie(jisu)).andExpect(jsonPath("$.data.restricted").value(false));
    }

    @Test
    void 운영자가_아니면_제한을_못_건다() throws Exception {
        postJson(minji, "/api/admin/sanctions", "{\"userId\":" + jisuId + ",\"reason\":\"ABUSE\"}")
                .andExpect(status().isForbidden());
        postJson(admin, "/api/admin/sanctions", "{\"userId\":999999,\"reason\":\"ABUSE\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40403"));
        postJson(admin, "/api/admin/sanctions", "{\"userId\":" + jisuId + ",\"reason\":\"ABUSE\",\"days\":0}")
                .andExpect(status().isBadRequest());
    }
}
