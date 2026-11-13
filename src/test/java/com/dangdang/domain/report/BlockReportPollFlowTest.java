package com.dangdang.domain.report;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BlockReportPollFlowTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie jisu;
    Cookie mina;
    String jisuProfile;
    String minaProfile;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com", "지수");
        mina = login("mina@x.com", "민아");
        jisuProfile = createProfile(jisu, "말랑한 두부");
        minaProfile = createProfile(mina, "포근한 고구마");
    }

    @AfterEach
    void clean() { // reports는 프로필이 지워져도 남고 posts도 남는다(SET NULL) → 먼저 지운다
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM posts");
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Cookie login(String email, String name) throws Exception {
        userRepository.save(User.ofEmail(email, name, passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private String createProfile(Cookie who, String nickname) throws Exception {
        String body = mvc.perform(post("/api/community/profiles").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private String write(Cookie who, String title) throws Exception {
        return idOf(mvc.perform(post("/api/posts").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MIND\",\"title\":\"" + title + "\",\"content\":\"본문\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String writePoll(Cookie who) throws Exception {
        return idOf(mvc.perform(post("/api/posts").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MEASUREMENT\",\"title\":\"투표\",\"content\":\"본문\","
                                + "\"poll\":[\"화장실\",\"자리 비움\",\"못 찾음\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String comment(Cookie who, String postId, String json) throws Exception {
        return idOf(mvc.perform(post("/api/posts/" + postId + "/comments").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String idOf(String body) {
        return JsonPath.read(body, "$.data.id");
    }

    private void block(Cookie who, String target) throws Exception {
        mvc.perform(post("/api/blocks").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockedProfileId\":\"" + target + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true));
    }

    private void report(Cookie who, String type, String id, String reason) throws Exception {
        mvc.perform(post("/api/reports").cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"" + type + "\",\"targetId\":\"" + id + "\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reported").value(true));
    }

    @Test
    void 차단은_다시_해도_200이고_한_줄() throws Exception {
        block(jisu, minaProfile);
        block(jisu, minaProfile);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM profile_blocks", Long.class)).isEqualTo(1L);
    }

    @Test
    void 자기_자신과_없는_프로필은_차단할_수_없다() throws Exception {
        mvc.perform(post("/api/blocks").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockedProfileId\":\"" + jisuProfile + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40003"));
        mvc.perform(post("/api/blocks").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockedProfileId\":\"999999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40402"));
        mvc.perform(post("/api/blocks").cookie(jisu).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 차단_목록은_최근_순이고_커서로_넘긴다() throws Exception {
        String third = createProfile(login("sora@x.com", "소라"), "졸린 감자");
        block(jisu, minaProfile);
        block(jisu, third);

        String first = mvc.perform(get("/api/blocks").param("size", "1").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].profileId").value(third))
                .andExpect(jsonPath("$.data.items[0].nickname").value("졸린 감자"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");

        mvc.perform(get("/api/blocks").param("size", "1").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].profileId").value(minaProfile))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(get("/api/blocks").param("cursor", "망가진커서").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40004"));
    }

    @Test
    void 차단_해제는_한_적_없어도_200() throws Exception {
        mvc.perform(delete("/api/blocks/" + minaProfile).cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false));
    }

    @Test
    void 차단한_사람의_글은_목록_검색_상세에서_사라지고_해제하면_돌아온다() throws Exception {
        String id = write(mina, "민아의 글");
        write(jisu, "지수의 글");
        block(jisu, minaProfile);

        mvc.perform(get("/api/posts").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("지수의 글"));
        mvc.perform(get("/api/posts").param("q", "민아").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/posts/" + id).cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40401"));

        mvc.perform(delete("/api/blocks/" + minaProfile).cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/posts").cookie(jisu)).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/posts/" + id).cookie(jisu)).andExpect(status().isOk());
    }

    @Test
    void 차단은_한쪽_방향이다() throws Exception {
        String id = write(jisu, "지수의 글");
        block(jisu, minaProfile);
        mvc.perform(get("/api/posts").cookie(mina)).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/posts/" + id).cookie(mina)).andExpect(status().isOk());
    }

    @Test
    void 차단한_사람에게는_공감_댓글_투표를_못_한다() throws Exception {
        String id = writePoll(mina);
        block(jisu, minaProfile);
        mvc.perform(post("/api/posts/" + id + "/reactions").cookie(jisu)).andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/" + id + "/comments").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/" + id + "/poll/votes").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 차단한_사람의_댓글과_그_답글은_목록과_개수에서_빠진다() throws Exception {
        Cookie sora = login("sora@x.com", "소라");
        createProfile(sora, "졸린 감자");
        String postId = write(jisu, "지수의 글");
        String minaComment = comment(mina, postId, "{\"content\":\"민아 댓글\"}");
        comment(sora, postId, "{\"parentId\":\"" + minaComment + "\",\"content\":\"소라 답글\"}");
        String soraComment = comment(sora, postId, "{\"content\":\"소라 댓글\"}");

        block(jisu, minaProfile);

        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(jisu))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(soraComment));
        mvc.perform(get("/api/posts/" + postId).cookie(jisu))
                .andExpect(jsonPath("$.data.commentCount").value(1));
        mvc.perform(get("/api/posts").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(1));
        mvc.perform(get("/api/posts/" + postId + "/comments").cookie(sora))
                .andExpect(jsonPath("$.data.totalCount").value(3));
    }

    @Test
    void 차단한_사람의_댓글에는_답글과_공감을_못_한다() throws Exception {
        String postId = write(jisu, "지수의 글");
        String minaComment = comment(mina, postId, "{\"content\":\"민아 댓글\"}");
        block(jisu, minaProfile);

        mvc.perform(post("/api/posts/" + postId + "/comments").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + minaComment + "\",\"content\":\"답글\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_40401"));
        mvc.perform(post("/api/comments/" + minaComment + "/reactions").cookie(jisu))
                .andExpect(status().isNotFound());
    }

    @Test
    void 삭제_가림_차단이_한_목록에서_모두_빠진다() throws Exception {
        String deleted = write(mina, "지운 글");
        String hidden = write(jisu, "가린 글");
        write(mina, "차단된 사람의 글");
        Cookie sora = login("sora@x.com", "소라");
        String soraProfile = createProfile(sora, "졸린 감자");
        write(sora, "남은 글");

        mvc.perform(delete("/api/posts/" + deleted).cookie(mina)).andExpect(status().isOk());
        jdbc.update("UPDATE posts SET hidden_reason = '광고' WHERE id = ?", Long.valueOf(hidden));
        block(jisu, minaProfile);

        mvc.perform(get("/api/posts").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("남은 글"))
                .andExpect(jsonPath("$.data.items[0].author.profileId").value(soraProfile));
    }

    @Test
    void 신고는_다시_해도_200이고_한_줄이며_글쓴이에게_알리지_않는다() throws Exception {
        String id = write(mina, "민아의 글");
        report(jisu, "POST", id, "MEDICAL_ADVICE");
        report(jisu, "POST", id, "MEDICAL_ADVICE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM reports", String.class)).isEqualTo("RECEIVED");
        mvc.perform(get("/api/posts/" + id).cookie(mina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("VISIBLE"));
    }

    @Test
    void 댓글도_신고할_수_있고_기타일_때만_내용을_저장한다() throws Exception {
        String postId = write(mina, "민아의 글");
        String commentId = comment(mina, postId, "{\"content\":\"민아 댓글\"}");

        mvc.perform(post("/api/reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"COMMENT\",\"targetId\":\"" + commentId
                                + "\",\"reason\":\"ETC\",\"detail\":\"이상해요\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":\"" + postId
                                + "\",\"reason\":\"ABUSE\",\"detail\":\"무시될 내용\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT detail FROM reports WHERE target_type = 'COMMENT'", String.class))
                .isEqualTo("이상해요");
        assertThat(jdbc.queryForObject("SELECT detail FROM reports WHERE target_type = 'POST'", String.class))
                .isNull();
    }

    @Test
    void 내_글_신고는_400_없는_대상은_404_틀린_값은_400() throws Exception {
        String id = write(jisu, "내 글");
        mvc.perform(post("/api/reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":\"" + id + "\",\"reason\":\"ETC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REPORT_40001"));
        mvc.perform(post("/api/reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":\"999999\",\"reason\":\"ETC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40401"));
        mvc.perform(post("/api/reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"COMMENT\",\"targetId\":\"999999\",\"reason\":\"ETC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMENT_40401"));
        mvc.perform(post("/api/reports").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":\"" + id + "\",\"reason\":\"없는이유\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 투표_전에는_항목별_숫자를_안_주고_투표하면_결과가_나온다() throws Exception {
        String id = writePoll(jisu);
        mvc.perform(get("/api/posts/" + id).cookie(mina))
                .andExpect(jsonPath("$.data.poll.totalVotes").value(0))
                .andExpect(jsonPath("$.data.poll.myOptionIndex").value((Object) null))
                .andExpect(jsonPath("$.data.poll.options[0].voteCount").value((Object) null));

        mvc.perform(post("/api/posts/" + id + "/poll/votes").cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVotes").value(1))
                .andExpect(jsonPath("$.data.myOptionIndex").value(1))
                .andExpect(jsonPath("$.data.options[0].voteCount").value(0))
                .andExpect(jsonPath("$.data.options[1].voteCount").value(1));

        mvc.perform(get("/api/posts/" + id).cookie(jisu))
                .andExpect(jsonPath("$.data.poll.totalVotes").value(1))
                .andExpect(jsonPath("$.data.poll.options[1].voteCount").value((Object) null));
        mvc.perform(get("/api/posts/" + id).cookie(mina))
                .andExpect(jsonPath("$.data.poll.myOptionIndex").value(1))
                .andExpect(jsonPath("$.data.poll.options[1].voteCount").value(1));
    }

    @Test
    void 같은_항목은_다시_200_다른_항목은_409() throws Exception {
        String id = writePoll(jisu);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/posts/" + id + "/poll/votes").cookie(mina).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"optionIndex\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalVotes").value(1));
        }
        mvc.perform(post("/api/posts/" + id + "/poll/votes").cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("POST_40901"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poll_votes", Long.class)).isEqualTo(1L);
    }

    @Test
    void 없는_항목과_투표_없는_글은_막는다() throws Exception {
        String poll = writePoll(jisu);
        mvc.perform(post("/api/posts/" + poll + "/poll/votes").cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40006"));
        mvc.perform(post("/api/posts/" + poll + "/poll/votes").cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));

        String plain = write(jisu, "투표 없는 글");
        mvc.perform(post("/api/posts/" + plain + "/poll/votes").cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40402"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/blocks")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
