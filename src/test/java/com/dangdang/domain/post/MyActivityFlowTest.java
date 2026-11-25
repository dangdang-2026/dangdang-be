package com.dangdang.domain.post;

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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 마이 › 커뮤니티 활동 (M-24 · M-25 · M-26) + 개수 (M-14). 고정 시계: 2026-11-09 13:00 (한국)
class MyActivityFlowTest extends ReminderTestBase {

    Cookie jisu;
    Cookie minji;
    String minjiProfileId;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        profile(jisu, "말랑한 두부");
        minjiProfileId = profile(minji, "포근한 고구마");
    }

    @AfterEach
    void cleanPosts() { // 글은 프로필이 지워져도 남는다(SET NULL) → 먼저 지운다. 댓글·공감은 글과 같이 지워진다
        jdbc.update("DELETE FROM posts");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String profile(Cookie who, String nickname) throws Exception {
        String res = postJson(who, "/api/community/profiles", "{\"nickname\":\"" + nickname + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private String write(Cookie who, String category, String title) throws Exception {
        String res = postJson(who, "/api/posts", "{\"category\":\"" + category + "\",\"title\":\"" + title
                + "\",\"content\":\"본문\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private String comment(Cookie who, String postId, String content) throws Exception {
        String res = postJson(who, "/api/posts/" + postId + "/comments", "{\"content\":\"" + content + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    private void react(Cookie who, String postId) throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(who)).andExpect(status().isOk());
    }

    private Cookie newUserWithoutProfile() throws Exception {   // 커뮤니티를 한 번도 안 써 본 사람
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        return login("hana@x.com");
    }

    private void later(long seconds) {
        clock.set(NOW.plusSeconds(seconds));
    }

    // ── 내가 쓴 글 (M-24) ──

    @Test
    void 내가_쓴_글은_내_것만_최신_먼저_커서로_나뉜다() throws Exception {
        write(jisu, "MEAL", "첫 글");
        later(60);
        String second = write(jisu, "MIND", "둘째 글");
        later(120);
        write(jisu, "BIRTH", "셋째 글");
        write(minji, "MEAL", "민지 글");
        comment(minji, second, "민지 댓글");

        String res = mvc.perform(get("/api/me/posts").param("size", "2").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", contains("셋째 글", "둘째 글")))
                .andExpect(jsonPath("$.data.items[1].category").value("MIND"))
                .andExpect(jsonPath("$.data.items[1].viewCount").value(0))
                .andExpect(jsonPath("$.data.items[1].commentCount").value(1))
                .andExpect(jsonPath("$.data.items[1].author.isMine").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(res, "$.data.nextCursor");

        mvc.perform(get("/api/me/posts").param("size", "2").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("첫 글")))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void 지운_글과_가린_글은_내가_쓴_글에_안_나온다() throws Exception {
        String deleted = write(jisu, "MIND", "지울 글");
        String hidden = write(jisu, "MIND", "가려질 글");
        write(jisu, "MIND", "남는 글");
        mvc.perform(delete("/api/posts/" + deleted).cookie(jisu)).andExpect(status().isOk());
        jdbc.update("UPDATE posts SET hidden_reason = 'MEDICAL_ADVICE' WHERE id = ?", Long.valueOf(hidden));

        mvc.perform(get("/api/me/posts").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("남는 글")));
    }

    // ── 내가 쓴 댓글 (M-25) ──

    @Test
    void 내가_쓴_댓글에_원글_번호와_제목_공감_수가_같이_온다() throws Exception {
        String postId = write(minji, "MEAL", "민지 글");
        String first = comment(jisu, postId, "좋은 글이에요");
        later(60);
        String reply = mvc.perform(post("/api/posts/" + postId + "/comments").cookie(jisu)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"저도 그래요\",\"parentId\":\"" + first + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        comment(minji, postId, "민지 댓글");
        mvc.perform(post("/api/comments/" + first + "/reactions").cookie(minji)).andExpect(status().isOk());

        mvc.perform(get("/api/me/comments").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].content", contains("저도 그래요", "좋은 글이에요")))
                .andExpect(jsonPath("$.data.items[0].parentId").value(first))
                .andExpect(jsonPath("$.data.items[0].id").value((String) JsonPath.read(reply, "$.data.id")))
                .andExpect(jsonPath("$.data.items[0].reactionCount").value(0))
                .andExpect(jsonPath("$.data.items[1].postId").value(postId))
                .andExpect(jsonPath("$.data.items[1].postTitle").value("민지 글"))
                .andExpect(jsonPath("$.data.items[1].parentId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].reactionCount").value(1));
    }

    @Test
    void 지운_댓글과_지운_글_가린_글의_댓글은_빠지고_커서로_나뉜다() throws Exception {
        String keep = write(minji, "MIND", "남는 글");
        String gone = write(minji, "MIND", "지워질 글");
        String hidden = write(minji, "MIND", "가려질 글");
        comment(jisu, keep, "하나");
        later(60);
        comment(jisu, keep, "둘");
        later(120);
        String mine = comment(jisu, keep, "지울 댓글");
        comment(jisu, gone, "지운 글 댓글");
        comment(jisu, hidden, "가린 글 댓글");
        mvc.perform(delete("/api/comments/" + mine).cookie(jisu)).andExpect(status().isOk());
        mvc.perform(delete("/api/posts/" + gone).cookie(minji)).andExpect(status().isOk());
        jdbc.update("UPDATE posts SET hidden_reason = 'MEDICAL_ADVICE' WHERE id = ?", Long.valueOf(hidden));

        String res = mvc.perform(get("/api/me/comments").param("size", "1").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].content", contains("둘")))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(res, "$.data.nextCursor");

        mvc.perform(get("/api/me/comments").param("size", "1").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].content", contains("하나")))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // ── 공감한 글 (M-26) ──

    @Test
    void 공감한_글은_공감한_때가_최신인_것_먼저() throws Exception {
        String first = write(minji, "MEAL", "먼저 쓴 글");
        later(60);
        String second = write(minji, "MIND", "나중 쓴 글");
        react(jisu, second);
        later(120);
        react(jisu, first);                       // 먼저 쓴 글에 나중에 공감 → 맨 위
        react(minji, first);

        String res = mvc.perform(get("/api/me/reactions").param("size", "1").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", contains("먼저 쓴 글")))
                .andExpect(jsonPath("$.data.items[0].reacted").value(true))
                .andExpect(jsonPath("$.data.items[0].reactionCount").value(2))
                .andExpect(jsonPath("$.data.items[0].author.displayName").value("포근한 고구마"))
                .andExpect(jsonPath("$.data.items[0].category").value("MEAL"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(res, "$.data.nextCursor");

        mvc.perform(get("/api/me/reactions").param("size", "1").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("나중 쓴 글")))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 안_보는_사람의_글과_가린_글은_공감한_글에서_빠진다() throws Exception {
        String minjiPost = write(minji, "MIND", "민지 글");
        Cookie hana = newUserWithoutProfile();
        react(jisu, minjiPost);
        String jisuPost = write(jisu, "MIND", "지수 글");
        react(jisu, jisuPost);
        String hidden = write(minji, "MIND", "가려질 글");
        react(jisu, hidden);
        jdbc.update("UPDATE posts SET hidden_reason = 'MEDICAL_ADVICE' WHERE id = ?", Long.valueOf(hidden));

        mvc.perform(get("/api/me/reactions").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("지수 글", "민지 글")));

        postJson(jisu, "/api/blocks", "{\"blockedProfileId\":" + minjiProfileId + "}").andExpect(status().isOk());

        mvc.perform(get("/api/me/reactions").cookie(jisu))
                .andExpect(jsonPath("$.data.items[*].title", contains("지수 글")));
        mvc.perform(get("/api/me/reactions").cookie(hana))   // 다른 사람에겐 영향 없음 (프로필도 없음)
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // ── 개수 (M-14) ──

    @Test
    void 개수는_목록과_같은_조건으로_센다() throws Exception {
        String a = write(jisu, "MIND", "지수 글 1");
        write(jisu, "MIND", "지수 글 2");
        String gone = write(jisu, "MIND", "지울 글");
        mvc.perform(delete("/api/posts/" + gone).cookie(jisu)).andExpect(status().isOk());
        String b = write(minji, "MIND", "민지 글");
        comment(jisu, a, "내 글에 댓글");
        comment(jisu, b, "민지 글에 댓글");
        String deletedComment = comment(jisu, b, "지울 댓글");
        mvc.perform(delete("/api/comments/" + deletedComment).cookie(jisu)).andExpect(status().isOk());
        react(jisu, a);
        react(jisu, b);
        comment(minji, a, "민지 댓글");          // 남의 댓글은 안 센다

        mvc.perform(get("/api/me/community-activity/counts").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postCount").value(2))
                .andExpect(jsonPath("$.data.commentCount").value(2))
                .andExpect(jsonPath("$.data.reactedPostCount").value(2))
                .andExpect(jsonPath("$.data.blockedCount").value(0));

        postJson(jisu, "/api/blocks", "{\"blockedProfileId\":" + minjiProfileId + "}").andExpect(status().isOk());

        // 민지를 안 보면: 민지 글의 댓글·공감은 목록에서 빠지니 개수에서도 빠진다
        mvc.perform(get("/api/me/community-activity/counts").cookie(jisu))
                .andExpect(jsonPath("$.data.postCount").value(2))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.reactedPostCount").value(1))
                .andExpect(jsonPath("$.data.blockedCount").value(1));
        mvc.perform(get("/api/me/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    // ── 프로필 없음 · 잘못된 요청 ──

    @Test
    void 프로필이_없으면_빈_목록과_0개() throws Exception {   // 마이 화면은 커뮤니티를 안 써 본 사람도 연다 → 404 아님
        Cookie hana = newUserWithoutProfile();

        mvc.perform(get("/api/me/posts").cookie(hana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(get("/api/me/comments").cookie(hana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/me/reactions").cookie(hana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/me/community-activity/counts").cookie(hana)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postCount").value(0))
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.reactedPostCount").value(0))
                .andExpect(jsonPath("$.data.blockedCount").value(0));
    }

    @Test
    void 다른_목록의_커서나_잘못된_size는_400() throws Exception {
        write(jisu, "MIND", "하나");
        write(jisu, "MIND", "둘");
        String res = mvc.perform(get("/api/me/posts").param("size", "1").cookie(jisu))
                .andReturn().getResponse().getContentAsString();
        String postsCursor = JsonPath.read(res, "$.data.nextCursor");

        mvc.perform(get("/api/me/reactions").param("cursor", postsCursor).cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40001"));
        mvc.perform(get("/api/me/comments").param("cursor", "아무글자").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40001"));
        mvc.perform(get("/api/me/posts").param("size", "0").cookie(jisu))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/posts").param("size", "101").cookie(jisu))
                .andExpect(status().isBadRequest());
    }
}
