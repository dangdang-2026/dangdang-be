package com.dangdang.domain.post;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PostFlowTest {
    private static final String BASE = "/api/posts";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie jisu;
    Cookie mina;

    @BeforeEach
    void setUp() throws Exception {
        jisu = loginWithProfile("jisu@x.com", "지수", "말랑한 두부");
        mina = loginWithProfile("mina@x.com", "민아", "포근한 고구마");
    }

    @AfterEach
    void clean() { // posts는 프로필이 지워져도 남으니(SET NULL) 먼저 지운다
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

    private Cookie loginWithProfile(String email, String name, String nickname) throws Exception {
        Cookie cookie = login(email, name);
        mvc.perform(post("/api/community/profiles").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isCreated());
        return cookie;
    }

    private String write(Cookie who, String category, String title) throws Exception {
        String body = mvc.perform(post(BASE).cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"" + category + "\",\"title\":\"" + title + "\",\"content\":\"본문\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    @Test
    void 글을_쓰면_201이고_id는_문자열() throws Exception {
        mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MEASUREMENT\",\"title\":\" 밖에서 어떻게 재세요? \",\"content\":\"궁금해요\","
                                + "\"poll\":[\"화장실\",\"자리 비움\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", isA(String.class)))
                .andExpect(jsonPath("$.data.title").value("밖에서 어떻게 재세요?"))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.author.displayName").value("말랑한 두부"))
                .andExpect(jsonPath("$.data.poll.options[1].text").value("자리 비움"));
    }

    @Test
    void 프로필이_없으면_글을_못_쓴다() throws Exception {
        Cookie noProfile = login("nope@x.com", "없음");
        mvc.perform(post(BASE).cookie(noProfile).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40401"));
    }

    @Test
    void 입력이_틀리면_400() throws Exception {
        for (String body : List.of(
                "{\"category\":\"MIND\",\"title\":\"  \",\"content\":\"본문\"}",
                "{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"\"}",
                "{\"title\":\"제목\",\"content\":\"본문\"}",
                "{\"category\":\"없는값\",\"title\":\"제목\",\"content\":\"본문\"}",
                "{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"본문\",\"poll\":[\"하나\"]}",
                "{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"본문\","
                        + "\"poll\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}")) {
            mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void 당당_소식은_일반_회원이_못_쓴다() throws Exception {
        mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NEWS\",\"title\":\"제목\",\"content\":\"본문\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("POST_40301"));
    }

    @Test
    void 글이_없어도_목록은_200_빈_배열() throws Exception {
        mvc.perform(get(BASE).cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value((Object) null))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 최신순으로_나오고_커서로_다음_페이지를_겹침_없이_읽는다() throws Exception {
        write(jisu, "MIND", "첫째");
        write(jisu, "MIND", "둘째");
        write(jisu, "MIND", "셋째");

        String first = mvc.perform(get(BASE).param("size", "2").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("셋째"))
                .andExpect(jsonPath("$.data.items[1].title").value("둘째"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");

        mvc.perform(get(BASE).param("size", "2").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("첫째"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value((Object) null));
    }

    @Test
    void 같은_시각의_글도_id로_순서가_갈려_빠지지_않는다() throws Exception {
        write(jisu, "MIND", "A");
        write(jisu, "MIND", "B");
        write(jisu, "MIND", "C");
        jdbc.update("UPDATE posts SET created_at = TIMESTAMPTZ '2026-11-09 10:00:00+09'");

        String first = mvc.perform(get(BASE).param("size", "2").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].title").value("C"))
                .andExpect(jsonPath("$.data.items[1].title").value("B"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(BASE).param("size", "2").param("cursor", (String) JsonPath.read(first, "$.data.nextCursor"))
                        .cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("A"));
    }

    @Test
    void 게시판과_검색어로_거른다() throws Exception {
        write(jisu, "MEAL", "저녁 메뉴 팁");
        write(jisu, "MIND", "자책하게 돼요");

        mvc.perform(get(BASE).param("category", "MEAL").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].category").value("MEAL"));
        mvc.perform(get(BASE).param("q", "자책").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("자책하게 돼요"));
        mvc.perform(get(BASE).param("q", "없는말").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 검색어의_퍼센트는_글자_그대로_찾는다() throws Exception {
        write(jisu, "MIND", "100% 확신");
        write(jisu, "MIND", "그냥 글");
        mvc.perform(get(BASE).param("q", "%").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void 다른_조건의_커서나_망가진_커서는_400() throws Exception {
        write(jisu, "MIND", "하나");
        write(jisu, "MIND", "둘");
        String first = mvc.perform(get(BASE).param("size", "1").cookie(jisu))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");

        mvc.perform(get(BASE).param("category", "MEAL").param("cursor", cursor).cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40001"));
        mvc.perform(get(BASE).param("cursor", "망가진커서").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40001"));
    }

    @Test
    void 인기순_기간_임신상태_필터는_보류라_400() throws Exception {
        for (String[] param : List.of(
                new String[] {"sort", "POPULAR"}, new String[] {"period", "WEEK"},
                new String[] {"audience", "PREGNANT"}, new String[] {"audience", "POSTPARTUM"})) {
            mvc.perform(get(BASE).param(param[0], param[1]).cookie(jisu))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("POST_40002"))
                    .andExpect(jsonPath("$.message", containsString("준비 중")));
        }
    }

    @Test
    void 기본값을_직접_보내면_통과하고_없는_값은_400() throws Exception {
        mvc.perform(get(BASE).param("sort", "LATEST").param("audience", "ALL").cookie(jisu))
                .andExpect(status().isOk());
        mvc.perform(get(BASE).param("sort", "OLDEST").cookie(jisu))
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(get(BASE).param("category", "DAILY").cookie(jisu))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("size", "0").cookie(jisu))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("q", "   ").cookie(jisu))
                .andExpect(jsonPath("$.errorCode").value("POST_40004"));
    }

    @Test
    void 상세를_보면_조회수가_오른다() throws Exception {
        String id = write(jisu, "MIND", "제목");
        mvc.perform(get(BASE + "/" + id).cookie(mina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(1))
                .andExpect(jsonPath("$.data.isMine").value(false))
                .andExpect(jsonPath("$.data.visibility").value("VISIBLE"));
        mvc.perform(get(BASE + "/" + id).cookie(mina))
                .andExpect(jsonPath("$.data.viewCount").value(2));
    }

    @Test
    void 글_번호가_이상하면_400_없으면_404() throws Exception {
        mvc.perform(get(BASE + "/abc").cookie(jisu)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/0").cookie(jisu)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/999999").cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40401"));
    }

    @Test
    void 익명_글은_이름과_프로필_id를_숨긴다() throws Exception {
        String body = mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MIND\",\"title\":\"제목\",\"content\":\"본문\",\"isAnonymous\":true}"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.id");
        mvc.perform(get(BASE + "/" + id).cookie(mina))
                .andExpect(jsonPath("$.data.author.displayName").value("익명 산모"))
                .andExpect(jsonPath("$.data.author.profileId").value((Object) null));
    }

    @Test
    void 내_글은_고칠_수_있고_보낸_칸만_바뀐다() throws Exception {
        String id = write(jisu, "MIND", "처음 제목");
        mvc.perform(patch(BASE + "/" + id).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바꾼 제목\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바꾼 제목"))
                .andExpect(jsonPath("$.data.content").value("본문"))
                .andExpect(jsonPath("$.data.category").value("MIND"));
    }

    @Test
    void 남의_글_수정_삭제는_404() throws Exception {
        String id = write(jisu, "MIND", "제목");
        mvc.perform(patch(BASE + "/" + id).cookie(mina).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"가로채기\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_40401"));
        mvc.perform(delete(BASE + "/" + id).cookie(mina))
                .andExpect(status().isNotFound());
    }

    @Test
    void 빈_수정_요청과_빈_제목은_400() throws Exception {
        String id = write(jisu, "MIND", "제목");
        mvc.perform(patch(BASE + "/" + id).cookie(jisu).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40003"));
        mvc.perform(patch(BASE + "/" + id).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("POST_40005"));
    }

    @Test
    void 지운_글은_줄이_남고_목록과_상세에서_사라진다() throws Exception {
        String id = write(jisu, "MIND", "지울 글");
        mvc.perform(delete(BASE + "/" + id).cookie(jisu)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE deleted_at IS NOT NULL", Long.class))
                .isEqualTo(1L);
        mvc.perform(get(BASE).cookie(jisu)).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get(BASE + "/" + id).cookie(jisu)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + id).cookie(jisu)).andExpect(status().isNotFound());
    }

    @Test
    void 가려진_글은_목록에서_빠지고_상세엔_이유만_나온다() throws Exception {
        String id = write(jisu, "MIND", "가려질 글");
        jdbc.update("UPDATE posts SET hidden_reason = '약 이름·용량 권유' WHERE id = ?", Long.valueOf(id));

        mvc.perform(get(BASE).cookie(mina)).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get(BASE).param("q", "가려질").cookie(mina)).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get(BASE + "/" + id).cookie(mina))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.data.hiddenReason").value("약 이름·용량 권유"))
                .andExpect(jsonPath("$.data.title").value((Object) null))
                .andExpect(jsonPath("$.data.content").value((Object) null));
        mvc.perform(patch(BASE + "/" + id).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"고쳐서 드러내기\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 임시_저장은_덮어쓰고_글을_올리면_없어진다() throws Exception {
        mvc.perform(put(BASE + "/drafts").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"쓰던 글\",\"isAnonymous\":true,\"poll\":[\"가\",\"\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("쓰던 글"));
        mvc.perform(put(BASE + "/drafts").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"MIND\",\"title\":\"더 쓴 글\",\"isAnonymous\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get(BASE + "/drafts").cookie(jisu))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("더 쓴 글"))
                .andExpect(jsonPath("$.data.items[0].isAnonymous").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        write(jisu, "MIND", "올린 글");
        mvc.perform(get(BASE + "/drafts").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
}
