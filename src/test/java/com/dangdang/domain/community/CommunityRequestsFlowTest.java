package com.dangdang.domain.community;

import com.dangdang.domain.user.entity.User;
import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 11/20 언니 요청: 자유·질문 게시판 · 임시 글 지우기 · 이용 제한 이의 신청 (내 커뮤니티 활동은 70번 MyActivityFlowTest)
class CommunityRequestsFlowTest extends ReminderTestBase {

    Cookie jisu;
    Cookie minji;
    Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        profile(jisu, "말랑한 두부");
        profile(minji, "포근한 고구마");
        userRepository.save(User.ofEmail("admin@x.com", "운영자", passwordEncoder.encode("abcd1234"), Instant.now()));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = 'admin@x.com'");
        admin = login("admin@x.com");
    }

    @AfterEach
    void cleanPosts() {   // posts는 프로필이 지워져도 남는다(SET NULL)
        jdbc.update("DELETE FROM posts");
    }

    private ResultActions postJson(Cookie who, String url, String json) throws Exception {
        return mvc.perform(post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void profile(Cookie who, String nickname) throws Exception {
        postJson(who, "/api/community/profiles", "{\"nickname\":\"" + nickname + "\"}").andExpect(status().isCreated());
    }

    private void write(Cookie who, String category, String title) throws Exception {
        postJson(who, "/api/posts", "{\"category\":\"" + category + "\",\"title\":\"" + title
                + "\",\"content\":\"본문\"}").andExpect(status().isCreated());
    }

    private void restrict(Long userId) throws Exception {
        postJson(admin, "/api/admin/sanctions", "{\"userId\":" + userId + ",\"reason\":\"MEDICAL_ADVICE\",\"days\":7}")
                .andExpect(status().isCreated());
    }

    // ── 자유·질문 게시판 ──

    @Test
    void 자유_질문_게시판에_쓰고_게시판별로_본다() throws Exception {
        write(jisu, "FREE", "자유 글");
        write(jisu, "QUESTION", "질문 글");

        mvc.perform(get("/api/posts").param("category", "FREE").cookie(minji))
                .andExpect(jsonPath("$.data.items[*].title", contains("자유 글")));
        mvc.perform(get("/api/posts").param("category", "QUESTION").cookie(minji))
                .andExpect(jsonPath("$.data.items[*].title", contains("질문 글")));
    }

    // ── 임시 글 지우기 ──

    @Test
    void 임시_글을_지우면_목록이_비고_없어도_200() throws Exception {
        mvc.perform(put("/api/posts/drafts").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"쓰다 만 글\"}")).andExpect(status().isOk());

        mvc.perform(delete("/api/posts/drafts").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/drafts").cookie(jisu)).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(delete("/api/posts/drafts").cookie(jisu)).andExpect(status().isOk());
    }

    // ── 이의 신청 ──

    @Test
    void 제한_중이면_이의_신청은_한_번만() throws Exception {
        restrict(jisuId);

        postJson(jisu, "/api/community/restriction/appeal", "{\"content\":\"경험을 나누려던 거예요\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("경험을 나누려던 거예요"))
                .andExpect(jsonPath("$.data.reason").value("MEDICAL_ADVICE"));
        mvc.perform(get("/api/community/restriction").cookie(jisu)).andExpect(jsonPath("$.data.appealed").value(true));

        postJson(jisu, "/api/community/restriction/appeal", "{\"content\":\"한 번 더\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40905"));
        mvc.perform(get("/api/admin/sanction-appeals").cookie(admin))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(jisuId));
    }

    @Test
    void 제한이_없으면_이의_신청은_409_빈_내용은_400() throws Exception {
        postJson(jisu, "/api/community/restriction/appeal", "{\"content\":\"이의\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40904"));
        restrict(jisuId);
        postJson(jisu, "/api/community/restriction/appeal", "{\"content\":\"  \"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/community/restriction").cookie(jisu)).andExpect(jsonPath("$.data.appealed").value(false));
    }

    @Test
    void 이의_신청_목록은_운영자만() throws Exception {
        mvc.perform(get("/api/admin/sanction-appeals").cookie(jisu)).andExpect(status().isForbidden());
    }
}
