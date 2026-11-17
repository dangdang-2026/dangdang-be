package com.dangdang.domain.meal;

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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 식사 항목 흐름 테스트. 음식은 마이그레이션으로 넣은 식약처 데이터를 씀
// 쫄면 D103-173000000-0001: 1인분 550g, 100g당 129kcal · 탄수화물 23.26g
@SpringBootTest
@AutoConfigureMockMvc
class MealItemFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    static final String JJOLMYEON = "D103-173000000-0001";

    Cookie access;   // 지수(주인공)
    Cookie other;    // 남의 식사 확인용 다른 사용자
    long mealId;

    @BeforeEach
    void setUp() throws Exception {
        access = signupAndLogin("jisu@x.com");
        other = signupAndLogin("minho@x.com");
        mealId = createMeal(access);
    }

    @AfterEach
    void clean() { // users를 지우면 meals·meal_items도 같이 지워짐 (ON DELETE CASCADE)
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private Cookie signupAndLogin(String email) throws Exception {
        userRepository.save(User.ofEmail(email, "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    private long createMeal(Cookie who) throws Exception {
        String body = mvc.perform(post("/api/meals").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mealSlot\":\"LUNCH\",\"startedAt\":\"" + Instant.now().minus(Duration.ofHours(1)) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.mealId")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions add(Cookie who, long meal, String json) throws Exception {
        return mvc.perform(post("/api/meals/" + meal + "/items").cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 식약처_음식을_고르면_1인분_영양값으로_추가된다() throws Exception {
        add(access, mealId, "{\"foodCode\":\"" + JJOLMYEON + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mealId").value(mealId))
                .andExpect(jsonPath("$.data.foodCode").value(JJOLMYEON))
                .andExpect(jsonPath("$.data.name").value("쫄면"))
                .andExpect(jsonPath("$.data.servings").value(1))
                .andExpect(jsonPath("$.data.weightG").value(nullValue()))
                .andExpect(jsonPath("$.data.unit").value("g"))
                .andExpect(jsonPath("$.data.nutritionSource").value("DB"))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(709.5))     // 129 × 550 / 100
                .andExpect(jsonPath("$.data.nutrition.carbG").value(127.9));   // 23.26 × 550 / 100
    }

    @Test
    void 이름은_보이는_이름으로_저장된다() throws Exception {
        add(access, mealId, "{\"foodCode\":\"D101-017070000-0001\"}")   // 볶음밥_김치
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("김치볶음밥"));
    }

    @Test
    void 인분이나_무게만큼_영양값이_바뀐다() throws Exception {
        // 반 인분 = 275g
        add(access, mealId, "{\"foodCode\":\"" + JJOLMYEON + "\",\"servings\":0.5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.servings").value(0.5))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(64.0))     // 23.26 × 275 / 100 = 63.965
                .andExpect(jsonPath("$.data.nutrition.kcal").value(354.8));    // 129 × 2.75 = 354.75
        // 잰 무게 300g이 있으면 인분 대신 무게로 계산
        add(access, mealId, "{\"foodCode\":\"" + JJOLMYEON + "\",\"servings\":2,\"weightG\":300}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.weightG").value(300))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(69.8));    // 23.26 × 3 = 69.78
    }

    @Test
    void 고른_그때의_영양값을_항목에_복사해_둔다() throws Exception {
        String body = add(access, mealId, "{\"foodCode\":\"" + JJOLMYEON + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long itemId = ((Number) JsonPath.read(body, "$.data.itemId")).longValue();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT serving_amount, kcal, carb_g, sugar_g, nutrition_source FROM meal_items WHERE id = ?", itemId);
        assertThat((BigDecimal) row.get("serving_amount")).isEqualByComparingTo("550");
        assertThat((BigDecimal) row.get("kcal")).isEqualByComparingTo("129");
        assertThat((BigDecimal) row.get("carb_g")).isEqualByComparingTo("23.26");
        assertThat((BigDecimal) row.get("sugar_g")).isEqualByComparingTo("3.51");
        assertThat(row.get("nutrition_source")).isEqualTo("DB");
    }

    @Test
    void 이름을_직접_적으면_적은_영양값만_있다() throws Exception {
        add(access, mealId, "{\"name\":\" 엄마표 잡채 \",\"nutrition\":{\"carbG\":40,\"kcal\":250.5}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.foodCode").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("엄마표 잡채"))
                .andExpect(jsonPath("$.data.nutritionSource").value("USER"))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(40.0))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(250.5))
                .andExpect(jsonPath("$.data.nutrition.sugarG").value(nullValue()));   // 안 적은 값은 모름
        // 영양값을 하나도 안 적어도 추가는 됨 (전부 모름)
        add(access, mealId, "{\"name\":\"떡\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nutrition.carbG").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(nullValue()));
    }

    @Test
    void 항목을_추가하면_식사_수정_시각도_바뀐다() throws Exception {
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("UPDATE meals SET updated_at = ? WHERE id = ?", Timestamp.from(past), mealId);
        add(access, mealId, "{\"name\":\"떡\"}").andExpect(status().isCreated());
        Timestamp updated = jdbc.queryForObject("SELECT updated_at FROM meals WHERE id = ?", Timestamp.class, mealId);
        assertThat(updated.toInstant()).isAfter(past);
    }

    @Test
    void 음식_고르기와_이름_적기는_둘_중_하나만() throws Exception {
        add(access, mealId, "{\"foodCode\":\"" + JJOLMYEON + "\",\"name\":\"쫄면\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40004"));
        add(access, mealId, "{\"servings\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40004"));
        add(access, mealId, "{\"name\":\"   \"}")   // 공백 이름은 안 보낸 것
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40004"));
    }

    @Test
    void 고른_음식에_영양값을_같이_보내면_400() throws Exception {
        add(access, mealId, "{\"foodCode\":\"" + JJOLMYEON + "\",\"nutrition\":{\"carbG\":10}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40005"));
    }

    @Test
    void 없는_음식이면_404() throws Exception {
        add(access, mealId, "{\"foodCode\":\"D999-000000000-0000\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40401"));
    }

    @Test
    void 남의_식사나_지운_식사에는_추가할_수_없다() throws Exception {
        add(other, mealId, "{\"name\":\"떡\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        mvc.perform(delete("/api/meals/" + mealId).cookie(access)).andExpect(status().is2xxSuccessful());
        add(access, mealId, "{\"name\":\"떡\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        add(access, 999999999L, "{\"name\":\"떡\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
    }

    @Test
    void 값_범위가_틀리면_400() throws Exception {
        String[] bodies = {
                "{\"name\":\"떡\",\"servings\":0}",                     // 0인분
                "{\"name\":\"떡\",\"servings\":21}",                    // 20인분 초과
                "{\"name\":\"떡\",\"servings\":0.333}",                 // 소수 셋째 자리
                "{\"name\":\"떡\",\"weightG\":-1}",                     // 음수 무게
                "{\"name\":\"떡\",\"nutrition\":{\"carbG\":-1}}",       // 음수 영양값
                "{\"name\":\"떡\",\"nutrition\":{\"carbG\":1.25}}",     // 영양값 소수 둘째 자리
                "{\"name\":\"" + "가".repeat(101) + "\"}",              // 101자 이름
        };
        for (String b : bodies) {
            add(access, mealId, b)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    // ---------- 수정 ----------

    private long addAndGetId(long meal, String json) throws Exception {
        String body = add(access, meal, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.itemId")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions patchItem(Cookie who, long meal, long item, String json) throws Exception {
        return mvc.perform(patch("/api/meals/" + meal + "/items/" + item).cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void 인분을_바꾸면_영양값도_다시_계산된다() throws Exception {
        long itemId = addAndGetId(mealId, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        patchItem(access, mealId, itemId, "{\"servings\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(itemId))
                .andExpect(jsonPath("$.data.servings").value(2))
                .andExpect(jsonPath("$.data.name").value("쫄면"))              // 안 보낸 칸은 그대로
                .andExpect(jsonPath("$.data.nutrition.carbG").value(255.9));   // 23.26 × 1100 / 100 = 255.86
    }

    @Test
    void 무게를_넣었다가_null로_지우면_다시_인분으로_계산된다() throws Exception {
        long itemId = addAndGetId(mealId, "{\"foodCode\":\"" + JJOLMYEON + "\",\"servings\":2}");
        patchItem(access, mealId, itemId, "{\"weightG\":300}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightG").value(300))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(69.8));    // 300g
        patchItem(access, mealId, itemId, "{\"servings\":1}")                 // 무게를 안 보내면 무게는 그대로
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightG").value(300))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(69.8));
        patchItem(access, mealId, itemId, "{\"weightG\":null}")               // null을 보내면 무게를 지움
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightG").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(127.9));   // 1인분 550g
    }

    @Test
    void 이름은_직접_적은_음식만_바꿀_수_있다() throws Exception {
        long custom = addAndGetId(mealId, "{\"name\":\"잡채\"}");
        patchItem(access, mealId, custom, "{\"name\":\" 엄마표 잡채 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("엄마표 잡채"));
        long food = addAndGetId(mealId, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        patchItem(access, mealId, food, "{\"name\":\"비빔쫄면\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40006"));
    }

    @Test
    void 바꿀_값이_없으면_400() throws Exception {
        long itemId = addAndGetId(mealId, "{\"name\":\"떡\"}");
        patchItem(access, mealId, itemId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40003"));
        patchItem(access, mealId, itemId, "{\"name\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40003"));
        patchItem(access, mealId, itemId, "{\"servings\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 항목을_수정하면_식사_수정_시각도_바뀐다() throws Exception {
        long itemId = addAndGetId(mealId, "{\"name\":\"떡\"}");
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("UPDATE meals SET updated_at = ? WHERE id = ?", Timestamp.from(past), mealId);
        patchItem(access, mealId, itemId, "{\"servings\":2}").andExpect(status().isOk());
        Timestamp updated = jdbc.queryForObject("SELECT updated_at FROM meals WHERE id = ?", Timestamp.class, mealId);
        assertThat(updated.toInstant()).isAfter(past);
    }

    @Test
    void 남의_항목이나_다른_식사의_항목이나_지운_항목은_404() throws Exception {
        long itemId = addAndGetId(mealId, "{\"name\":\"떡\"}");
        patchItem(other, mealId, itemId, "{\"servings\":2}")                  // 남의 식사
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        long otherMeal = createMeal(access);
        patchItem(access, otherMeal, itemId, "{\"servings\":2}")              // 내 다른 식사 주소로는 못 고침
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
        jdbc.update("UPDATE meal_items SET deleted_at = now() WHERE id = ?", itemId);
        patchItem(access, mealId, itemId, "{\"servings\":2}")                 // 지운 항목
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(post("/api/meals/" + mealId + "/items")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"떡\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/meals/" + mealId + "/items/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\":2}"))
                .andExpect(status().isUnauthorized());
    }
}
