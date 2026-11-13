package com.dangdang.domain.comment;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CommentReactionFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie jisu;
    Cookie mina;
    String postId;

    @BeforeEach
    void setUp() throws Exception {
        jisu = loginWithProfile("jisu@x.com", "지수", "말랑한 두부");
        mina = loginWithProfile("mina@x.com", "민아", "포근한 고구마");
        String body = mvc.perform(post("/api/posts").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MEASUREMENT\",\"title\":\"제목\",\"content\":\"본문\"}"))
                .andReturn().getResponse().getContentAsString();
        postId = JsonPath.read(body, "$.data.id");
    }

    @AfterEach
    void clean() { // posts를 지우면 comments·공감도 같이 지워진다 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Cookie loginWithProfile(String email, String name, String nickname) throws Exception {
        userRepository.save(User.ofEmail(email, name, passwordEncoder.encode("abcd1234"), Instant.now()));
        Cookie cookie = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
        mvc.perform(post("/api/community/profiles").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isCreated());
        return cookie;
    }

    private String comment(Cookie who, String json) throws Exception {
        String body = mvc.perform(post("/api/posts/" + postId + "/comments").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String comment(Cookie who) throws Exception {
        return comment(who, "{\"content\":\"댓글\"}");
    }

    @Test
    void 댓글을_쓰면_201이고_id는_문자열() throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/comments").cookie(mina)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  저는 미리 확인해요. \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", isA(String.class)))
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.parentId").value((Object) null))
                .andExpect(jsonPath("$.data.content").value("저는 미리 확인해요."))
                .andExpect(jsonPath("$.data.author.displayName").value("포근한 고구마"))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.visibility").value("VISIBLE"));
    }

    @Test
    void 빈_댓글은_400_프로필_없으면_404() throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/comments").cookie(mina)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));

        userRepository.save(User.ofEmail("nope@x.com", "없음", passwordEncoder.encode("abcd1234"), Instant.now()));
        Cookie noProfile = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
        mvc.perform(post("/api/posts/" + postId + "/comments").cookie(noProfile)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40401"));
    }

    @Test
    void 답글은_부모를_문자열로_받고_목록은_오래된_순() throws Exception {
        String parent = comment(mina);
        String reply = comment(jisu, "{\"parentId\":\"" + parent + "\",\"content\":\"답글\"}");

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(mina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(parent))
                .andExpect(jsonPath("$.data.items[1].id").value(reply))
                .andExpect(jsonPath("$.data.items[1].parentId").value(parent))
                .andExpect(jsonPath("$.data.items[0].isMine").value(true))
                .andExpect(jsonPath("$.data.items[1].isMine").value(false));
    }

    @Test
    void 답글의_답글은_400_다른_글의_댓글은_404() throws Exception {
        String parent = comment(mina);
        String reply = comment(jisu, "{\"parentId\":\"" + parent + "\",\"content\":\"답글\"}");
        mvc.perform(post("/api/posts/" + postId + "/comments").cookie(mina)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + reply + "\",\"content\":\"답글의 답글\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_40001"));

        String other = JsonPath.read(mvc.perform(post("/api/posts").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MIND\",\"title\":\"다른 글\",\"content\":\"본문\"}"))
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        mvc.perform(post("/api/posts/" + other + "/comments").cookie(mina)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + parent + "\",\"content\":\"엉뚱한 부모\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_40401"));
    }

    @Test
    void 지운_글에는_댓글을_못_쓰고_목록도_404() throws Exception {
        mvc.perform(delete("/api/posts/" + postId).cookie(jisu)).andExpect(status().isOk());
        mvc.perform(post("/api/posts/" + postId + "/comments").cookie(mina)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"댓글\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40401"));
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(mina))
                .andExpect(status().isNotFound());
    }

    @Test
    void 내_댓글은_고치고_지울_수_있고_남의_댓글은_404() throws Exception {
        String id = comment(mina);
        mvc.perform(patch("/api/comments/" + id).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"가로채기\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_40401"));
        mvc.perform(delete("/api/comments/" + id).cookie(jisu)).andExpect(status().isNotFound());

        mvc.perform(patch("/api/comments/" + id).cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고친 댓글\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("고친 댓글"));
        mvc.perform(delete("/api/comments/" + id).cookie(mina)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comments WHERE deleted_at IS NOT NULL", Long.class))
                .isEqualTo(1L);
        mvc.perform(patch("/api/comments/" + id).cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"지운 뒤\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 지운_댓글과_가린_댓글은_답글까지_목록에서_빠지고_개수도_줄어든다() throws Exception {
        String deleted = comment(mina);
        comment(jisu, "{\"parentId\":\"" + deleted + "\",\"content\":\"지운 댓글의 답글\"}");
        String hidden = comment(mina);
        comment(jisu, "{\"parentId\":\"" + hidden + "\",\"content\":\"가린 댓글의 답글\"}");
        String kept = comment(mina);

        mvc.perform(delete("/api/comments/" + deleted).cookie(mina)).andExpect(status().isOk());
        jdbc.update("UPDATE comments SET hidden_reason = '약 이름·용량' WHERE id = ?", Long.valueOf(hidden));

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(kept));
        mvc.perform(get("/api/posts/" + postId).cookie(jisu))
                .andExpect(jsonPath("$.data.commentCount").value(1));
        mvc.perform(get("/api/posts").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(1));
    }

    @Test
    void 가린_댓글은_고칠_수_없다() throws Exception {
        String id = comment(mina);
        jdbc.update("UPDATE comments SET hidden_reason = '약 이름·용량' WHERE id = ?", Long.valueOf(id));
        mvc.perform(patch("/api/comments/" + id).cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"고쳐서 드러내기\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 익명_댓글은_이름을_숨긴다() throws Exception {
        comment(mina, "{\"content\":\"익명 댓글\",\"isAnonymous\":true}");
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].author.displayName").value("익명 산모"))
                .andExpect(jsonPath("$.data.items[0].author.profileId").value((Object) null));
    }

    @Test
    void 글_공감은_두_번_눌러도_200이고_한_줄() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(mina))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reacted").value(true))
                    .andExpect(jsonPath("$.data.reactionCount").value(1));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_reactions", Long.class)).isEqualTo(1L);
    }

    @Test
    void 글_공감_취소는_공감한_적_없어도_200() throws Exception {
        mvc.perform(delete("/api/posts/" + postId + "/reactions/me").cookie(mina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reacted").value(false))
                .andExpect(jsonPath("$.data.reactionCount").value(0));

        mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(mina)).andExpect(status().isOk());
        mvc.perform(delete("/api/posts/" + postId + "/reactions/me").cookie(mina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reacted").value(false))
                .andExpect(jsonPath("$.data.reactionCount").value(0));
    }

    @Test
    void 공감_수와_내_공감이_목록과_상세에_보인다() throws Exception {
        mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(mina)).andExpect(status().isOk());
        comment(mina);

        mvc.perform(get("/api/posts").cookie(mina))
                .andExpect(jsonPath("$.data.items[0].reactionCount").value(1))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reacted").value(true));
        mvc.perform(get("/api/posts").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].reacted").value(false));
        mvc.perform(get("/api/posts/" + postId).cookie(mina))
                .andExpect(jsonPath("$.data.reactionCount").value(1))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.reacted").value(true));
    }

    @Test
    void 지운_글과_가린_글에는_공감할_수_없다() throws Exception {
        jdbc.update("UPDATE posts SET hidden_reason = '광고' WHERE id = ?", Long.valueOf(postId));
        mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(mina))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40401"));
        jdbc.update("UPDATE posts SET hidden_reason = NULL, deleted_at = now() WHERE id = ?", Long.valueOf(postId));
        mvc.perform(post("/api/posts/" + postId + "/reactions").cookie(mina))
                .andExpect(status().isNotFound());
    }

    @Test
    void 댓글_공감도_두_번_눌러도_한_줄이고_목록에_보인다() throws Exception {
        String id = comment(mina);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/comments/" + id + "/reactions").cookie(jisu))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reacted").value(true))
                    .andExpect(jsonPath("$.data.reactionCount").value(1));
        }
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].reactionCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reacted").value(true));
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(mina))
                .andExpect(jsonPath("$.data.items[0].reacted").value(false));

        mvc.perform(delete("/api/comments/" + id + "/reactions/me").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reacted").value(false))
                .andExpect(jsonPath("$.data.reactionCount").value(0));
        mvc.perform(delete("/api/comments/" + id + "/reactions/me").cookie(jisu))
                .andExpect(status().isOk());
    }

    @Test
    void 지운_댓글에는_공감할_수_없다() throws Exception {
        String id = comment(mina);
        mvc.perform(delete("/api/comments/" + id).cookie(mina)).andExpect(status().isOk());
        mvc.perform(post("/api/comments/" + id + "/reactions").cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_40401"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(status().isUnauthorized());
    }
}
