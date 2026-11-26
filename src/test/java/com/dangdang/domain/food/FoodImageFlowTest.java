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
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
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
    void AI가_찾을_이름을_주면_그_이름으로_식약처를_찾고_화면_이름은_AI_이름() throws Exception {
        // dangdang-ai #44: DB엔 계란찜이 없고 달걀찜만 있음 → searchName으로 찾아야 음식이 붙음
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("계란찜", 0.93, "달걀찜"), new Candidate("김밥", 0.05, " "))));   // 빈 찾을 이름은 AI 이름으로

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("계란찜"))
                .andExpect(jsonPath("$.data.candidates[0].foods", not(empty())))
                .andExpect(jsonPath("$.data.candidates[0].foods[0].name").value(org.hamcrest.Matchers.containsString("달걀찜")))
                .andExpect(jsonPath("$.data.candidates[0].foods[0].unitLabel").value("그릇"))   // 사진 후보 음식에도 표시 단위
                .andExpect(jsonPath("$.data.candidates[1].name").value("김밥"))
                .andExpect(jsonPath("$.data.candidates[1].foods", not(empty())));
    }

    @Test
    void 식약처에_없는_이름이면_foods는_빈_배열() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("없는음식이름쀍", 0.95))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].foods", hasSize(0)));
    }

    @Test
    void 일등_확신도가_낮으면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 0.69), new Candidate("비빔밥", 0.2))));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)));
    }

    @Test
    void 일등_확신도가_딱_기준값이면_후보_화면() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 0.7))));

        analyze(PHOTO).andExpect(jsonPath("$.data.fallback").value(false));
    }

    @Test
    void AI_서버에_연결이_안_되면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willThrow(new ResourceAccessException("timeout"));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void AI_서버_모델이_없으면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any(), any()))
                .willThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null));

        analyze(PHOTO).andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void AI_서버_토큰이_틀려도_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any(), any()))
                .willThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null));

        analyze(PHOTO).andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void 사진으로_열_수_없는_파일이면_400() throws Exception {
        given(foodImageClient.candidates(any(), any(), any()))
                .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "", null, null, null));

        analyze(new MockMultipartFile("image", "a.heic", "image/heic", new byte[]{1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40002"));
    }

    @Test
    void AI_서버가_용량_초과로_거절하면_413() throws Exception {
        given(foodImageClient.candidates(any(), any(), any()))
                .willThrow(HttpClientErrorException.create(HttpStatus.CONTENT_TOO_LARGE, "", null, null, null));

        analyze(PHOTO)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_41301"));
    }

    @Test
    void AI_서버가_요청_형식을_거절하면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any(), any()))
                .willThrow(HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "", null, null, null));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void 확신도가_0에서_1_밖이면_뺀다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 45), new Candidate("라면", Double.NaN), new Candidate("비빔밥", 0.2))));

        analyze(PHOTO)   // 남는 건 비빔밥 0.2뿐이라 기준 0.7 미만
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void 사진이_없으면_400() throws Exception {
        mvc.perform(multipart("/api/foods/analyze-image").cookie(access))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40001"));
        analyze(new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40001"));
        verify(foodImageClient, never()).candidates(any(), any(), any());
    }

    @Test
    void 십MB_넘는_사진이면_413() throws Exception {
        analyze(new MockMultipartFile("image", "big.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_41301"));
        verify(foodImageClient, never()).candidates(any(), any(), any());
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
        verify(foodImageClient, never()).candidates(any(), any(), any());
    }

    @Test
    void 사진_AI_동의를_철회했으면_403() throws Exception {
        consent("AI_PHOTO", false);

        analyze(PHOTO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40301"));
        verify(foodImageClient, never()).candidates(any(), any(), any());
    }

    // ---------- 한상 사진 (F-13) ----------

    @Test
    void AI가_위치를_안_주면_사진_전체를_음식_하나로_본다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
                new Candidate("김밥", 0.95))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"))   // 예전 칸 그대로
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].box").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].fallback").value(false))
                .andExpect(jsonPath("$.data.items[0].candidates[0].name").value("김밥"));
    }

    @Test
    void AI가_그릇을_하나만_찾으면_그_위치와_사진_전체_후보로_음식_하나() throws Exception {
        // dangdang-ai #40: 그릇이 1개면 items 1개에 그 위치, 후보는 사진 전체 후보와 같음
        List<Candidate> whole = List.of(new Candidate("김밥", 0.92));
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(whole,
                List.of(new FoodImageClient.Item(new FoodImageClient.Box(0.2, 0.3, 0.5, 0.4), whole))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].box.x").value(0.2))
                .andExpect(jsonPath("$.data.items[0].box.height").value(0.4))
                .andExpect(jsonPath("$.data.items[0].candidates[0].name").value("김밥"));
    }

    @Test
    void AI가_그릇을_못_찾으면_box_null인_음식_하나() throws Exception {
        // dangdang-ai #40: 그릇이 0개거나 그릇 찾기 모델이 없으면 items 1개, box null
        List<Candidate> whole = List.of(new Candidate("김밥", 0.92));
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(whole,
                List.of(new FoodImageClient.Item(null, whole))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].box").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].candidates[0].name").value("김밥"));
    }

    @Test
    void 한상_사진이면_음식마다_위치와_후보를_주고_예전_칸은_가장_확신_높은_음식() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(
                List.of(new Candidate("김치찌개", 0.5)),
                List.of(new FoodImageClient.Item(new FoodImageClient.Box(0.05, 0.1, 0.4, 0.3),
                                List.of(new Candidate("비빔밥", 0.8))),
                        new FoodImageClient.Item(new FoodImageClient.Box(0.5, 0.1, 0.4, 0.3),
                                List.of(new Candidate("김밥", 0.95), new Candidate("비빔밥", 0.03))),
                        new FoodImageClient.Item(new FoodImageClient.Box(0.3, 0.6, 0.3, 0.3),
                                List.of(new Candidate("김치", 0.4))))));   // 확신 낮음 → 이 음식만 이름으로 적기

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"))   // 1등 확신도가 가장 높은 음식
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].box.x").value(0.05))
                .andExpect(jsonPath("$.data.items[0].box.width").value(0.4))
                .andExpect(jsonPath("$.data.items[0].candidates[0].name").value("비빔밥"))
                .andExpect(jsonPath("$.data.items[0].candidates[0].foods", not(empty())))
                .andExpect(jsonPath("$.data.items[1].candidates[0].name").value("김밥"))
                .andExpect(jsonPath("$.data.items[2].fallback").value(true))
                .andExpect(jsonPath("$.data.items[2].candidates", hasSize(0)))
                .andExpect(jsonPath("$.data.items[2].box.y").value(0.6));
    }

    @Test
    void 위치가_틀리면_위치만_빼고_음식은_6개까지() throws Exception {
        List<FoodImageClient.Item> many = new java.util.ArrayList<>();
        many.add(new FoodImageClient.Item(new FoodImageClient.Box(0.8, 0.1, 0.5, 0.2), List.of(new Candidate("김밥", 0.9))));   // 오른쪽으로 넘침
        many.add(new FoodImageClient.Item(new FoodImageClient.Box(Double.NaN, 0.0, 0.1, 0.1), List.of(new Candidate("김밥", 0.9))));
        for (int i = 0; i < 6; i++) {
            many.add(new FoodImageClient.Item(new FoodImageClient.Box(0.0, 0.0, 0.1, 0.1), List.of(new Candidate("김밥", 0.9))));
        }
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(), many));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.items", hasSize(6)))
                .andExpect(jsonPath("$.data.items[0].box").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[1].box").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[2].box.width").value(0.1));
    }

    @Test
    void 위치가_있으면_가운데_점도_준다_좌표가_비면_위치만_뺀다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(),
                List.of(new FoodImageClient.Item(new FoodImageClient.Box(0.2, 0.4, 0.4, 0.2), List.of(new Candidate("김밥", 0.9))),
                        new FoodImageClient.Item(new FoodImageClient.Box(null, 0.1, 0.1, 0.1), List.of(new Candidate("김밥", 0.9))))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.items[0].point.x").value(0.4))     // 0.2 + 0.4 / 2
                .andExpect(jsonPath("$.data.items[0].point.y").value(0.5))     // 0.4 + 0.2 / 2
                .andExpect(jsonPath("$.data.items[1].box").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[1].point").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[1].fallback").value(false));   // 위치만 빠지고 후보는 그대로
    }

    @Test
    void 위치도_후보도_없는_빈_음식만_오면_예전_candidates로_처리한다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(
                List.of(new Candidate("김밥", 0.95)), List.of(new FoodImageClient.Item(null, List.of()))));   // {}

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void 끝이_1을_살짝_넘는_위치는_사진_경계에_맞게_줄인다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(),
                List.of(new FoodImageClient.Item(new FoodImageClient.Box(0.6, 0.7, 0.4005, 0.3), List.of(new Candidate("김밥", 0.9))))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.items[0].box.x").value(0.6))
                .andExpect(jsonPath("$.data.items[0].box.width").value(org.hamcrest.Matchers.closeTo(0.4, 1e-9)))   // 1 - 0.6
                .andExpect(jsonPath("$.data.items[0].box.height").value(org.hamcrest.Matchers.closeTo(0.3, 1e-9)));
    }

    @Test
    void 이름_없는_후보의_높은_점수로는_6개_안에_못_든다() throws Exception {
        List<FoodImageClient.Item> seven = new java.util.ArrayList<>();
        seven.add(new FoodImageClient.Item(new FoodImageClient.Box(0.0, 0.0, 0.1, 0.1), List.of(new Candidate(" ", 0.99))));   // 이름 없음
        for (int i = 1; i < 7; i++) {
            seven.add(new FoodImageClient.Item(new FoodImageClient.Box(0.1 * i, 0.0, 0.1, 0.1), List.of(new Candidate("비빔밥", 0.8))));
        }
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(), seven));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.items", hasSize(6)))
                .andExpect(jsonPath("$.data.items[*].fallback", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
    }

    @Test
    void items가_빈_값뿐이면_예전_candidates로_처리한다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(
                List.of(new Candidate("김밥", 0.95)), java.util.Arrays.asList((FoodImageClient.Item) null)));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void 음식이_6개보다_많으면_확신_높은_6개를_남기고_순서는_그대로() throws Exception {
        List<FoodImageClient.Item> seven = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            seven.add(new FoodImageClient.Item(new FoodImageClient.Box(0.1 * i, 0.0, 0.1, 0.1), List.of(new Candidate("비빔밥", 0.5))));
        }
        seven.add(new FoodImageClient.Item(new FoodImageClient.Box(0.9, 0.9, 0.1, 0.1), List.of(new Candidate("김밥", 0.95))));   // 맨 뒤지만 확신 가장 높음
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(), seven));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.items", hasSize(6)))
                .andExpect(jsonPath("$.data.items[5].candidates[0].name").value("김밥"))   // 남았고, 원래 순서대로 맨 뒤
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"));
    }

    @Test
    void 한상_사진의_음식이_전부_확신_낮으면_전체_이름으로_적기지만_위치는_준다() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(),
                List.of(new FoodImageClient.Item(new FoodImageClient.Box(0.0, 0.0, 0.5, 0.5), List.of(new Candidate("김밥", 0.3))))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].fallback").value(true));
    }

    @Test
    void 음식마다_확신이_낮아도_사진_전체_후보가_높으면_예전_칸은_사진_전체_후보로() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(
                List.of(new Candidate("비빔밥", 0.95)),
                List.of(new FoodImageClient.Item(new FoodImageClient.Box(0.0, 0.0, 0.4, 0.4), List.of(new Candidate("김밥", 0.3))),
                        new FoodImageClient.Item(new FoodImageClient.Box(0.5, 0.5, 0.4, 0.4), List.of()))));

        analyze(PHOTO)
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.candidates[0].name").value("비빔밥"))
                .andExpect(jsonPath("$.data.candidates[0].foods", not(empty())))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].fallback").value(true))   // 음식마다 판단은 그대로
                .andExpect(jsonPath("$.data.items[1].fallback").value(true));
    }

    @Test
    void AI_후보를_확신도_순으로_다시_세우고_3개까지만() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
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
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(java.util.Arrays.asList(
                new Candidate(null, 0.95), new Candidate(" ", 0.9), new Candidate("김밥", 0.8))));

        analyze(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                .andExpect(jsonPath("$.data.candidates[0].name").value("김밥"));
    }

    @Test
    void 이름이_빈_후보만_오면_이름으로_적기() throws Exception {
        given(foodImageClient.candidates(any(), any(), any())).willReturn(new FoodImageClient.Result(List.of(
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
