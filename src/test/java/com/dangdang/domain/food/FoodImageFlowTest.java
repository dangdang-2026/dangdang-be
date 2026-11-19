package com.dangdang.domain.food;

import com.dangdang.domain.food.infra.FoodImageClient;
import com.dangdang.domain.food.infra.FoodImageClient.Candidate;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 음식 사진 AI 분석 흐름 테스트. AI 서버는 가짜로 바꾸고, 음식 검색은 마이그레이션으로 넣은 식약처 데이터를 씀
@SpringBootTest
@AutoConfigureMockMvc
class FoodImageFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean FoodImageClient foodImageClient;   // 진짜 AI 서버 대신

    static final MockMultipartFile PHOTO =
            new MockMultipartFile("image", "lunch.jpg", "image/jpeg", new byte[]{1, 2, 3});

    Cookie access;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.save(User.ofEmail("jisu@x.com", "지수", passwordEncoder.encode("abcd1234"), Instant.now()));
        access = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
        consent("AI_PHOTO", true);   // 동의 확인 테스트 말고는 동의한 상태로 시작
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 후보마다_식약처_음식을_붙여서_돌려준다() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 0.9972), new Candidate("김치볶음밥", 0.0019), new Candidate("비빔밥", 0.0007))));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"))
                .andExpect(jsonPath("$.data.candidates[0].confidence").value(0.9972))
                .andExpect(jsonPath("$.data.candidates[0].foods", not(empty())))
                .andExpect(jsonPath("$.data.candidates[0].foods.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.candidates[0].foods[0].foodCode").exists())
                .andExpect(jsonPath("$.data.candidates[1].name").value("김치볶음밥"));
    }

    @Test
    void 식약처에_없는_이름이면_foods는_빈_배열() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("없는음식이름쀍", 0.95))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].foods", hasSize(0)));
    }

    @Test
    void 일등_확신도가_낮으면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 0.69), new Candidate("비빔밥", 0.2))));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)));
    }

    @Test
    void 일등_확신도가_딱_기준값이면_후보_화면() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 0.7))));

        analyze(PHOTO).andExpect(jsonPath("$.data.fallback").value(false));
    }

    @Test
    void AI_서버에_연결이_안_되면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any())).willThrow(new ResourceAccessException("timeout"));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void AI_서버_모델이_없으면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any()))
                .willThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null));

        analyze(PHOTO).andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void AI_서버_토큰이_틀려도_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any()))
                .willThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null));

        analyze(PHOTO).andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void 사진으로_열_수_없는_파일이면_400() throws Exception {
        given(foodImageClient.candidates(any(), any()))
                .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null, null, null));

        analyze(new MockMultipartFile("image", "a.heic", "image/heic", new byte[]{1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40002"));
    }

    @Test
    void 사진이_없으면_400() throws Exception {
        mvc.perform(multipart("/api/foods/analyze-image").cookie(access))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40001"));
        analyze(new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40001"));
        verify(foodImageClient, never()).candidates(any(), any());
    }

    @Test
    void 십MB_넘는_사진이면_413() throws Exception {
        analyze(new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_41301"));
        verify(foodImageClient, never()).candidates(any(), any());
    }

    @Test
    void multipart가_아니면_415() throws Exception {
        mvc.perform(post("/api/foods/analyze-image").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_41501"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(multipart("/api/foods/analyze-image").file(PHOTO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 사진_AI_동의를_안_했으면_403이고_AI_서버로_보내지_않는다() throws Exception {
        jdbc.update("DELETE FROM consent_events");   // 한 번도 동의 안 한 상태

        analyze(PHOTO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40301"));
        verify(foodImageClient, never()).candidates(any(), any());
    }

    @Test
    void 사진_AI_동의를_철회했으면_403() throws Exception {
        consent("AI_PHOTO", false);

        analyze(PHOTO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40301"));
        verify(foodImageClient, never()).candidates(any(), any());
    }

    @Test
    void AI_후보를_확신도_순으로_다시_세우고_3개까지만() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("비빔밥", 0.01), new Candidate("김밥", 0.9), new Candidate("라면", 0.02),
                new Candidate("김치볶음밥", 0.05), new Candidate("떡볶이", 0.001))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[*].name")
                        .value(org.hamcrest.Matchers.contains("김밥", "김치볶음밥", "라면")));
    }

    @Test
    void 이름이_빈_후보는_뺀다() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(java.util.Arrays.asList(
                new Candidate(null, 0.95), new Candidate(" ", 0.9), new Candidate("김밥", 0.8))));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"));
    }

    @Test
    void 이름이_빈_후보만_오면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate(null, 0.95))));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true));
    }

    private void consent(String type, boolean granted) throws Exception {
        mvc.perform(post("/api/me/consents").cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"" + type + "\",\"granted\":" + granted + "}]}"))
                .andExpect(status().isOk());
    }

    private ResultActions analyze(MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/foods/analyze-image").file(file).cookie(access));
    }
}
