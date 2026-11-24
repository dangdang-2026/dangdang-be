package com.dangdang.domain.auth;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.kakao.KakaoClient;
import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                case "no-email" -> new KakaoUser("1002", null, null);            // 이메일 없음·인증 안 됨 (KakaoApiClient가 null로 줌)
                case "long-name" -> new KakaoUser("1003", "long@x.com", "열글자가넘는아주긴카카오닉네임");   // 이메일 추가 (H)
                case "jisu" -> new KakaoUser("1004", "jisu@x.com", "지수");
                case "no-name" -> new KakaoUser("1005", "noname@x.com", null);   // 새로 (H) — 닉네임 동의 안 함
                default -> throw new CustomException(AuthErrorCode.KAKAO_LOGIN_FAILED); // 틀린·쓴 코드
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

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

    private String tokenOf(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.data.kakaoToken");
    }

    private ResultActions signup(String token) throws Exception {
        return mvc.perform(post("/api/auth/kakao/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakaoToken\":\"" + token + "\"}"));
    }

    private void signupWith(String code) throws Exception {   // 카카오 → K-08 「가입할게요」 두 단계
        signup(tokenOf(kakao(code, REDIRECT))).andExpect(status().isCreated());
    }

    private Cookie emailLogin(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private ResultActions link(Cookie who, String json) throws Exception {
        return mvc.perform(post("/api/me/kakao").cookie(who).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // 리뷰: 여러 요청을 한꺼번에 출발시키고(CountDownLatch) 「상태 에러코드」만 모은다 (성공은 상태만) — 순서는 안 본다
    private List<String> together(List<Callable<ResultActions>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<ResultActions> call : calls) {
            futures.add(pool.submit(() -> {
                start.await();
                var res = call.call().andReturn().getResponse();
                return res.getStatus() < 300 ? String.valueOf(res.getStatus())
                        : res.getStatus() + " " + JsonPath.read(res.getContentAsString(), "$.errorCode");
            }));
        }
        start.countDown();
        List<String> statuses = new ArrayList<>();
        for (Future<String> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();
        return statuses;
    }

    @Test
    void 두_번째부터는_같은_회원으로_로그인만() throws Exception {
        signupWith("minji");
        kakao("minji", REDIRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("LOGGED_IN"))
                .andExpect(cookie().exists("ACCESS_TOKEN"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 이메일이_없으면_카카오_가입은_400이고_계정은_안_생긴다() throws Exception {
        String token = tokenOf(kakao("no-email", REDIRECT)
                .andExpect(jsonPath("$.data.result").value("NEEDS_SIGNUP")));   // 토큰은 준다 → 이메일 계정 연결(L-10)은 된다
        signup(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40012"));
        assertThat(userRepository.count()).isZero();
    }

    @Test   // 리뷰: 가입이 400으로 막혀도 토큰은 남는다 → K-08 「이메일로 로그인 → 연결」이 AUTH_40011로 막히지 않게
    void 이메일이_없어_가입이_막혀도_같은_토큰으로_이메일_계정에_연결된다() throws Exception {
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        String token = tokenOf(kakao("no-email", REDIRECT));
        signup(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_40012"));

        link(emailLogin("hana@x.com"), "{\"kakaoToken\":\"" + token + "\"}").andExpect(status().isOk());
        kakao("no-email", REDIRECT).andExpect(jsonPath("$.data.result").value("LOGGED_IN"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_이메일_계정이_있어_가입이_409여도_같은_토큰으로_연결된다() throws Exception {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
        String token = tokenOf(kakao("jisu", REDIRECT));
        signup(token).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AUTH_40902"));

        link(emailLogin("jisu@x.com"), "{\"kakaoToken\":\"" + token + "\"}").andExpect(status().isOk());
        signup(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_40011"));   // 연결에 썼으니 끝
    }

    @Test   // 리뷰: 저장 때 이메일 UNIQUE(ux_users_email)에 걸리면 카카오 중복(40903)이 아니라 이메일 계정 있음(40902)
    void 가입_저장이_이메일_UNIQUE에_걸리면_409_AUTH_40902() throws Exception {
        // 대소문자만 다른 예전 데이터 → existsByEmail(정확히 같은 글자)은 지나가고 lower(email) 인덱스에서 막힌다 (동시 가입과 같은 길)
        jdbc.update("INSERT INTO users (email, display_name, auth_provider, password_hash) VALUES ('Minji@X.com', '민지', 'EMAIL', 'x')");
        String token = tokenOf(kakao("minji", REDIRECT).andExpect(jsonPath("$.data.result").value("NEEDS_SIGNUP")));
        signup(token).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AUTH_40902"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test   // 리뷰: 같은 토큰으로 가입을 동시에 눌러도 계정은 하나 — 하나는 201, 나머지는 AUTH_40011
    void 같은_토큰으로_동시에_가입해도_한_번만() throws Exception {
        String token = tokenOf(kakao("minji", REDIRECT));
        List<String> statuses = together(List.of(() -> signup(token), () -> signup(token)));

        assertThat(statuses).containsExactlyInAnyOrder("201", "400 AUTH_40011");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 닉네임이_없으면_이름은_기본값() throws Exception {
        signupWith("no-name");
        User saved = userRepository.findByEmail("noname@x.com").orElseThrow();
        assertThat(saved.getDisplayName()).isEqualTo("당당이");
    }

    @Test
    void 닉네임이_10글자를_넘으면_잘라서_저장() throws Exception {
        signupWith("long-name");
        assertThat(userRepository.findAll().get(0).getDisplayName()).hasSize(10);
    }

    @Test
    void 처음이면_계정을_안_만들고_토큰만_주고_가입하기를_누르면_만들어진다() throws Exception {
        String token = tokenOf(kakao("minji", REDIRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("NEEDS_SIGNUP"))
                .andExpect(cookie().doesNotExist("ACCESS_TOKEN")));
        assertThat(userRepository.count()).isZero();

        signup(token).andExpect(status().isCreated())
                .andExpect(cookie().httpOnly("ACCESS_TOKEN", true))
                .andExpect(cookie().exists("REFRESH_TOKEN"));
        User saved = userRepository.findByEmail("minji@x.com").orElseThrow();
        assertThat(saved.getProviderSubject()).isEqualTo("1001");
        assertThat(saved.getDisplayName()).isEqualTo("민지");
        assertThat(saved.getPasswordHash()).isNull();

        kakao("minji", REDIRECT).andExpect(jsonPath("$.data.result").value("LOGGED_IN"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 가입_토큰은_한_번만() throws Exception {
        String token = tokenOf(kakao("minji", REDIRECT));
        signup(token).andExpect(status().isCreated());
        signup(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("AUTH_40011"));
    }

    @Test
    void 같은_이메일_계정이_있으면_가린_이메일과_토큰_로그인_뒤_연결하면_카카오로_그_계정에() throws Exception {
        User jisu = userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
        String token = tokenOf(kakao("jisu", REDIRECT)
                .andExpect(jsonPath("$.data.result").value("EMAIL_ACCOUNT_EXISTS"))
                .andExpect(jsonPath("$.data.email").value("jisu@x.com"))
                .andExpect(jsonPath("$.data.maskedEmail").value("ji***@x.com")));

        link(emailLogin("jisu@x.com"), "{\"kakaoToken\":\"" + token + "\"}").andExpect(status().isOk());

        kakao("jisu", REDIRECT).andExpect(jsonPath("$.data.result").value("LOGGED_IN"));
        assertThat(userRepository.count()).isEqualTo(1);                        // 기록이 하나로
        assertThat(userRepository.findById(jisu.getId()).orElseThrow().getAuthProvider()).isEqualTo(AuthProvider.EMAIL);
    }

    @Test
    void 이메일_없는_카카오도_K08에서_이메일로_로그인하면_연결된다() throws Exception {
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        String token = tokenOf(kakao("no-email", REDIRECT));
        Cookie hana = emailLogin("hana@x.com");
        link(hana, "{\"kakaoToken\":\"" + token + "\"}").andExpect(status().isOk());

        mvc.perform(get("/api/me").cookie(hana))
                .andExpect(jsonPath("$.data.loginMethods.email").value(true))
                .andExpect(jsonPath("$.data.loginMethods.kakao").value(true));
    }

    @Test
    void M02에서_코드로_연결하고_두_번째는_409() throws Exception {
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        Cookie hana = emailLogin("hana@x.com");
        link(hana, "{\"code\":\"minji\",\"redirectUri\":\"" + REDIRECT + "\"}").andExpect(status().isOk());
        link(hana, "{\"code\":\"jisu\",\"redirectUri\":\"" + REDIRECT + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AUTH_40904"));
    }

    @Test
    void 다른_계정에_붙은_카카오는_409() throws Exception {
        signupWith("minji");                                                       // 민지 카카오로 가입한 계정
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        link(emailLogin("hana@x.com"), "{\"code\":\"minji\",\"redirectUri\":\"" + REDIRECT + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("AUTH_40903"));
    }

    @Test   // 리뷰: 두 이메일 계정이 같은 카카오를 동시에 연결 → 하나만 200, 나머지는 AUTH_40903 (UNIQUE + flush)
    void 두_계정이_같은_카카오를_동시에_연결하면_하나만() throws Exception {
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        userRepository.save(User.ofEmail("dul@x.com", "둘", passwordEncoder.encode("abcd1234"), Instant.now()));
        Cookie hana = emailLogin("hana@x.com");
        Cookie dul = emailLogin("dul@x.com");
        String byCode = "{\"code\":\"minji\",\"redirectUri\":\"" + REDIRECT + "\"}";

        List<String> statuses = together(List.of(() -> link(hana, byCode), () -> link(dul, byCode)));

        assertThat(statuses).containsExactlyInAnyOrder("200", "409 AUTH_40903");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE provider_subject = '1001'", Long.class)).isEqualTo(1);
    }

    @Test
    void 토큰과_코드를_같이_보내면_400() throws Exception {
        userRepository.save(User.ofEmail("hana@x.com", "하나", passwordEncoder.encode("abcd1234"), Instant.now()));
        link(emailLogin("hana@x.com"), "{\"kakaoToken\":\"t\",\"code\":\"minji\",\"redirectUri\":\"" + REDIRECT + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 카카오로_가입한_계정은_로그인_방식이_카카오만() throws Exception {
        signupWith("minji");
        Cookie minji = kakao("minji", REDIRECT).andReturn().getResponse().getCookie("ACCESS_TOKEN");
        mvc.perform(get("/api/me").cookie(minji))
                .andExpect(jsonPath("$.data.loginMethods.email").value(false))
                .andExpect(jsonPath("$.data.loginMethods.kakao").value(true));
    }

    @Test
    void 이메일이_빈_예전_카카오_계정은_다시_로그인하면_이메일이_채워진다() throws Exception {
        userRepository.save(User.ofKakao("1001", "민지", null));                  // 이메일 필수 전에 가입한 사람
        kakao("minji", REDIRECT).andExpect(jsonPath("$.data.result").value("LOGGED_IN"));
        assertThat(userRepository.findByEmail("minji@x.com")).isPresent();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 그_이메일을_다른_계정이_쓰면_채우지_않고_로그인은_된다() throws Exception {
        userRepository.save(User.ofEmail("minji@x.com", "민지", passwordEncoder.encode("abcd1234"), Instant.now()));
        User old = userRepository.save(User.ofKakao("1001", "민지", null));
        kakao("minji", REDIRECT).andExpect(jsonPath("$.data.result").value("LOGGED_IN"));
        assertThat(userRepository.findById(old.getId()).orElseThrow().getEmail()).isNull();
    }

    @Test   // 리뷰: 대소문자만 다른 이메일 계정(정규화 전 데이터)이면 예전엔 existsByEmail을 지나 커밋 때 ux_users_email → 500
    void 대소문자만_다른_이메일_계정이_있어도_채우지_않고_로그인은_200() throws Exception {
        jdbc.update("INSERT INTO users (email, display_name, auth_provider, password_hash) VALUES ('Minji@X.com', '민지', 'EMAIL', 'x')");
        User old = userRepository.save(User.ofKakao("1001", "민지", null));
        kakao("minji", REDIRECT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("LOGGED_IN"))
                .andExpect(cookie().exists("ACCESS_TOKEN"));
        assertThat(userRepository.findById(old.getId()).orElseThrow().getEmail()).isNull();
    }

    @Test
    void 카카오로_가입한_이메일로는_이메일_가입이_막힌다() throws Exception {
        signupWith("minji");
        mvc.perform(post("/api/auth/email/availability").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Minji@X.com\"}"))                    // 대소문자 달라도
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.authProvider").value("KAKAO"));         // J-07
        mvc.perform(post("/api/auth/email/code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minji@x.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40901"));            // 인증번호도 안 감
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