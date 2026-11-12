package com.dangdang.domain.community;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
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

import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CommunityProfileFlowTest {
    private static final String BASE = "/api/community/profiles";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie jisu;
    Cookie mina;

    @BeforeEach
    void loginFirst() throws Exception {
        jisu = login("jisu@x.com", "지수");
        mina = login("mina@x.com", "민아");
    }

    @AfterEach
    void clean() { // users를 지우면 community_profiles도 같이 지워진다 (ON DELETE CASCADE)
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

    private void createProfile(Cookie who, String nickname) throws Exception {
        mvc.perform(post(BASE).cookie(who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void 아무도_안_쓰는_이름이면_available_true() throws Exception {
        mvc.perform(get(BASE + "/nickname-availability").param("nickname", "포근한 고구마").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void 이미_쓰는_이름이면_available_false() throws Exception {
        createProfile(mina, "말랑한 두부");
        mvc.perform(get(BASE + "/nickname-availability").param("nickname", "  말랑한 두부 ").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 닉네임_형식이_틀리면_400() throws Exception {
        for (String bad : List.of("가", "열한글자가넘는닉네임이에요", "두부!", "   ")) {
            mvc.perform(get(BASE + "/nickname-availability").param("nickname", bad).cookie(jisu))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40001"));
        }
    }

    @Test
    void nickname_쿼리가_아예_없으면_400() throws Exception {
        mvc.perform(get(BASE + "/nickname-availability").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 생성하면_201이고_id는_문자열_배지는_기본_false() throws Exception {
        mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  말랑한 두부 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id", isA(String.class)))
                .andExpect(jsonPath("$.data.nickname").value("말랑한 두부"))
                .andExpect(jsonPath("$.data.showWeekBadge").value(false));
    }

    @Test
    void 남이_쓰는_이름으로_만들면_409() throws Exception {
        createProfile(mina, "말랑한 두부");
        mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"말랑한 두부\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40901"));
    }

    @Test
    void 프로필이_이미_있으면_409() throws Exception {
        createProfile(jisu, "말랑한 두부");
        mvc.perform(post(BASE).cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새 이름\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40902"));
    }

    @Test
    void 프로필이_없으면_내_프로필_조회는_404() throws Exception {
        mvc.perform(get(BASE + "/me").cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40401"));
    }

    @Test
    void 닉네임과_배지를_따로_바꿀_수_있다() throws Exception {
        createProfile(jisu, "말랑한 두부");
        mvc.perform(patch(BASE + "/me").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"포근한 고구마\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("포근한 고구마"))
                .andExpect(jsonPath("$.data.showWeekBadge").value(false));
        mvc.perform(patch(BASE + "/me").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showWeekBadge\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/me").cookie(jisu))
                .andExpect(jsonPath("$.data.nickname").value("포근한 고구마"))
                .andExpect(jsonPath("$.data.showWeekBadge").value(true));
    }

    @Test
    void 내_이름_그대로는_통과_남의_이름은_409_빈_요청은_400() throws Exception {
        createProfile(jisu, "말랑한 두부");
        createProfile(mina, "포근한 고구마");
        mvc.perform(patch(BASE + "/me").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"말랑한 두부\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(BASE + "/me").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"포근한 고구마\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40901"));
        mvc.perform(patch(BASE + "/me").cookie(jisu).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMUNITY_40002"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get(BASE + "/me")).andExpect(status().isUnauthorized());
    }
}
