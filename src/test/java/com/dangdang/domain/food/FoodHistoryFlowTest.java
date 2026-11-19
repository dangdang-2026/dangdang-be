package com.dangdang.domain.food;

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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 같은 음식 지난 기록 흐름 테스트. 음식은 마이그레이션으로 넣은 식약처 데이터를 씀
// 쫄면 D103-173000000-0001: 1인분 550g
@SpringBootTest
@AutoConfigureMockMvc
class FoodHistoryFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    static final String JJOLMYEON = "D103-173000000-0001";

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 기록 확인용 다른 사용자

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
    }

    @AfterEach
    void clean() { // users를 지우면 meals·meal_items·readings도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 이_음식을_먹은_식사가_최신순으로_먹은_양과_같이_나온다() throws Exception {
        long older = createMeal(access, Instant.now().minus(Duration.ofDays(2)));
        long newer = createMeal(access, Instant.now().minus(Duration.ofHours(3)));
        addItem(access, older, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        addItem(access, newer, "{\"foodCode\":\"" + JJOLMYEON + "\",\"servings\":0.5}");

        history(access, JJOLMYEON, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].mealId").value(contains((int) newer, (int) older)))
                .andExpect(jsonPath("$.data.items[0].mealSlot").value("LUNCH"))
                .andExpect(jsonPath("$.data.items[0].items[0].foodCode").value(JJOLMYEON))
                .andExpect(jsonPath("$.data.items[0].items[0].servings").value(0.5))   // 지난번에 먹은 양
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 한_식사에_같은_음식이_여러_번_있어도_식사_하나로_묶인다() throws Exception {
        long meal = createMeal(access, Instant.now().minus(Duration.ofHours(3)));
        addItem(access, meal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        addItem(access, meal, "{\"foodCode\":\"" + JJOLMYEON + "\",\"servings\":2}");
        addItem(access, meal, "{\"name\":\"보리차\"}");   // 다른 음식은 안 나옴

        history(access, JJOLMYEON, "")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].items[*].servings").value(contains(1.0, 2.0)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 식사에_연결된_혈당만_붙는다() throws Exception {
        Instant startedAt = Instant.now().minus(Duration.ofHours(3));
        long meal = createMeal(access, startedAt);
        addItem(access, meal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        long linked = createReading(access, 130, startedAt.plus(Duration.ofHours(1)), meal);
        createReading(access, 140, startedAt.plus(Duration.ofMinutes(61)), null);   // 시간은 가깝지만 연결 안 됨

        history(access, JJOLMYEON, "")
                .andExpect(jsonPath("$.data.items[0].readings", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].readings[0].readingId").value(linked))
                .andExpect(jsonPath("$.data.items[0].readings[0].value").value(130))
                .andExpect(jsonPath("$.data.items[0].readings[0].mealId").value(meal));
    }

    @Test
    void 혈당이_없는_식사는_빈_배열() throws Exception {
        long meal = createMeal(access, Instant.now().minus(Duration.ofHours(3)));
        addItem(access, meal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");

        history(access, JJOLMYEON, "")
                .andExpect(jsonPath("$.data.items[0].readings", hasSize(0)));
    }

    @Test
    void 지운_식사와_지운_항목과_남의_식사는_빠진다() throws Exception {
        long deletedMeal = createMeal(access, Instant.now().minus(Duration.ofHours(5)));
        addItem(access, deletedMeal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        mvc.perform(delete("/api/meals/" + deletedMeal).cookie(access)).andExpect(status().isOk());

        long itemDeletedMeal = createMeal(access, Instant.now().minus(Duration.ofHours(4)));
        long item = addItem(access, itemDeletedMeal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        mvc.perform(delete("/api/meals/" + itemDeletedMeal + "/items/" + item).cookie(access))
                .andExpect(status().isOk());

        long othersMeal = createMeal(other, Instant.now().minus(Duration.ofHours(3)));
        addItem(other, othersMeal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");

        history(access, JJOLMYEON, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 먹은_적이_없는_코드면_빈_목록() throws Exception {
        history(access, "NO-SUCH-CODE", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 페이지로_나눠서_본다() throws Exception {
        for (int h = 1; h <= 3; h++) {
            long meal = createMeal(access, Instant.now().minus(Duration.ofHours(h)));
            addItem(access, meal, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        }

        history(access, JJOLMYEON, "?page=0&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        history(access, JJOLMYEON, "?page=1&size=2")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 페이지_범위가_틀리면_400() throws Exception {
        history(access, JJOLMYEON, "?size=101").andExpect(status().isBadRequest());
        history(access, JJOLMYEON, "?page=-1").andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/foods/" + JJOLMYEON + "/history")).andExpect(status().isUnauthorized());
    }

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private long createMeal(Cookie who, Instant startedAt) throws Exception {
        String body = mvc.perform(post("/api/meals").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + startedAt + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.mealId")).longValue();
    }

    private long addItem(Cookie who, long meal, String json) throws Exception {
        String body = mvc.perform(post("/api/meals/" + meal + "/items").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.itemId")).longValue();
    }

    private long createReading(Cookie who, int value, Instant measuredAt, Long mealId) throws Exception {
        String body = mvc.perform(post("/api/readings").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + ",\"measuredAt\":\"" + measuredAt
                                + "\",\"readingType\":\"POST_MEAL\",\"mealId\":" + mealId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.readingId")).longValue();
    }

    private ResultActions history(Cookie who, String code, String query) throws Exception {
        return mvc.perform(get("/api/foods/" + code + "/history" + query).cookie(who));
    }
}
