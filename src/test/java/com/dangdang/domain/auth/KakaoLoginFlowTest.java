package com.dangdang.domain.auth;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.kakao.KakaoClient;
import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class KakaoLoginFlowTest {

    static final String REDIRECT = "http://localhost:5173/auth/kakao/callback";

    // 진짜 카카오 대신: code 글자에 따라 정해진 사람을 돌려준다
    @TestConfiguration
    static class FakeKakao {
        @Bean
        @Primary
        KakaoClient fakeKakaoClient() {
            return (code, redirectUri) -> switch (code) {
                case "minji" -> new KakaoUser("1001", "minji@x.com", "민지");
                case "no-email" -> new KakaoUser("1002", null, null);
                case "long-name" -> new KakaoUser("1003", null, "열글자가넘는아주긴카카오닉네임");
                case "jisu" -> new KakaoUser("1004", "jisu@x.com", "지수");
                default -> throw new CustomException(AuthErrorCode.KAKAO_LOGIN_FAILED); // 틀린·쓴 코드
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ResultActions kakao(String code, String redirectUri) throws Exception {
        return mvc.perform(post("/api/auth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"redirectUri\":\"" + redirectUri + "\"}"));
    }

    @Test
    void 처음이면_가입되고_쿠키_두_개와_newUser_true() throws Exception {
        kakao("minji", REDIRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUser").value(true))
                .andExpect(cookie().httpOnly("ACCESS_TOKEN", true))
                .andExpect(cookie().exists("REFRESH_TOKEN"));

        User saved = userRepository.findByEmail("minji@x.com").orElseThrow();
        assertThat(saved.getDisplayName()).isEqualTo("민지");
        assertThat(saved.getProviderSubject()).isEqualTo("1001");
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    void 두_번째부터는_같은_회원으로_로그인만() throws Exception {
        kakao("minji", REDIRECT);
        kakao("minji", REDIRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newUser").value(false));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 이메일_동의를_안_해도_가입되고_이름은_기본값() throws Exception {
        kakao("no-email", REDIRECT).andExpect(status().isOk());
        User saved = userRepository.findAll().get(0);
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getDisplayName()).isEqualTo("당당이");
    }

    @Test
    void 닉네임이_10글자를_넘으면_잘라서_저장() throws Exception {
        kakao("long-name", REDIRECT).andExpect(status().isOk());
        assertThat(userRepository.findAll().get(0).getDisplayName()).hasSize(10);
    }

    @Test
    void 같은_이메일로_이메일_가입한_계정이_있으면_합치지_않고_409() throws Exception {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", "hash", Instant.now()));
        kakao("jisu", REDIRECT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40902"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 등록_안_된_리다이렉트_주소는_400() throws Exception {
        kakao("minji", "https://evil.example.com/callback")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40007"));
    }

    @Test
    void 카카오가_코드를_거절하면_400() throws Exception {
        kakao("used-code", REDIRECT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40006"));
    }
}