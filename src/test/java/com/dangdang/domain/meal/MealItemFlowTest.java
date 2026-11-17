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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        // 저장된 영양값을 다시 읽어도 그대로 (DB JSON → 객체로 되돌리기가 깨지지 않음)
        long itemId = addAndGetId(mealId, "{\"name\":\"잡채\",\"nutrition\":{\"carbG\":40}}");
        patchItem(access, mealId, itemId, "{\"servings\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutrition.carbG").value(40.0));     // 직접 적은 값은 먹은 양 전체 기준이라 인분이 바뀌어도 그대로
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

    // ---------- 영양값 직접 입력 ----------

    private org.springframework.test.web.servlet.ResultActions postNutrition(Cookie who, long meal, long item, String json) throws Exception {
        return mvc.perform(post("/api/meals/" + meal + "/items/" + item + "/nutrition").cookie(who)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // 항목을 추가하고 {itemId, 응답의 mealUpdatedAt} 돌려줌
    private Object[] addWithMealTime(String json) throws Exception {
        String body = add(access, mealId, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Object[]{((Number) JsonPath.read(body, "$.data.itemId")).longValue(),
                JsonPath.read(body, "$.data.mealUpdatedAt")};
    }

    @Test
    void 적은_칸만_사용자_값이고_안_적은_칸은_공공_값으로_계산된다() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\"}");
        postNutrition(access, mealId, (long) added[0],
                "{\"weightG\":250,\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\"" + added[1] + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutritionSource").value("USER"))
                .andExpect(jsonPath("$.data.weightG").value(250))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(45.0))            // 적은 값 그대로 (무게로 다시 곱하지 않음)
                .andExpect(jsonPath("$.data.nutrition.kcal").value(322.5))            // 안 적은 칸: 129 × 250 / 100
                .andExpect(jsonPath("$.data.nutritionOverride.carbG").value(45))
                .andExpect(jsonPath("$.data.nutritionOverride.kcal").value(nullValue()))
                .andExpect(jsonPath("$.data.nutritionOverride.empty").doesNotExist());   // 내부 메서드가 JSON 칸으로 새지 않음
        // DB에 저장된 JSON에도 적은 칸만 있음 (다시 읽을 때 깨지지 않게)
        String saved = jdbc.queryForObject("SELECT nutrition_override::text FROM meal_items WHERE id = ?", String.class, added[0]);
        assertThat(saved).doesNotContain("empty");
        // 공공 음식 데이터는 그대로
        BigDecimal carb = jdbc.queryForObject("SELECT carb_g FROM foods WHERE food_code = ?", BigDecimal.class, JJOLMYEON);
        assertThat(carb).isEqualByComparingTo("23.26");
    }

    @Test
    void 응답의_mealUpdatedAt이나_식사_조회의_updatedAt으로_이어서_고칠_수_있다() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\"}");
        String first = postNutrition(access, mealId, (long) added[0],
                        "{\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\"" + added[1] + "\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(first, "$.data.mealUpdatedAt");
        postNutrition(access, mealId, (long) added[0],
                        "{\"nutritionOverride\":{\"carbG\":50},\"expectedUpdatedAt\":\"" + next + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutrition.carbG").value(50.0));
        // 식사 상세의 updatedAt도 그대로 쓸 수 있음
        String meal = mvc.perform(get("/api/meals/" + mealId).cookie(access))
                .andReturn().getResponse().getContentAsString();
        String mealUpdatedAt = JsonPath.read(meal, "$.data.updatedAt");
        postNutrition(access, mealId, (long) added[0],
                        "{\"nutritionOverride\":{\"carbG\":55},\"expectedUpdatedAt\":\"" + mealUpdatedAt + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutrition.carbG").value(55.0));
    }

    @Test
    void 그_사이_식사가_바뀌었으면_409() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\"}");
        patchItem(access, mealId, (long) added[0], "{\"servings\":2}").andExpect(status().isOk());   // 다른 곳에서 먼저 고침
        postNutrition(access, mealId, (long) added[0],
                        "{\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\"" + added[1] + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40901"));
        // 덮어쓰지 않았음
        mvc.perform(get("/api/meals/" + mealId).cookie(access)).andExpect(status().isOk());
        String source = jdbc.queryForObject("SELECT nutrition_source FROM meal_items WHERE id = ?", String.class, added[0]);
        assertThat(source).isEqualTo("DB");
    }

    @Test
    void 무게는_안_보내면_그대로_null을_보내면_지운다() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\",\"weightG\":300}");
        String r1 = postNutrition(access, mealId, (long) added[0],
                        "{\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\"" + added[1] + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightG").value(300))
                .andReturn().getResponse().getContentAsString();
        postNutrition(access, mealId, (long) added[0],
                        "{\"weightG\":null,\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\""
                                + JsonPath.read(r1, "$.data.mealUpdatedAt") + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightG").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(709.5));          // 다시 1인분 550g으로 계산
    }

    @Test
    void 영양값_직접_입력_입력값이_틀리면_400() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\"}");
        long itemId = (long) added[0];
        postNutrition(access, mealId, itemId, "{\"nutritionOverride\":{},\"expectedUpdatedAt\":\"" + added[1] + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40007"));
        String[] bodies = {
                "{\"expectedUpdatedAt\":\"" + added[1] + "\"}",                                   // nutritionOverride 없음
                "{\"nutritionOverride\":{\"carbG\":45}}",                                            // expectedUpdatedAt 없음
                "{\"nutritionOverride\":{\"carbG\":-1},\"expectedUpdatedAt\":\"" + added[1] + "\"}", // 음수
                "{\"weightG\":0,\"nutritionOverride\":{\"carbG\":1},\"expectedUpdatedAt\":\"" + added[1] + "\"}", // 무게 0
        };
        for (String b : bodies) {
            postNutrition(access, mealId, itemId, b)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
    }

    @Test
    void 남의_식사나_지운_항목의_영양값은_못_고친다() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\"}");
        String body = "{\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\"" + added[1] + "\"}";
        postNutrition(other, mealId, (long) added[0], body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        jdbc.update("UPDATE meal_items SET deleted_at = now() WHERE id = ?", added[0]);
        postNutrition(access, mealId, (long) added[0], body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
    }

    // ---------- 공공 영양값 되돌리기 ----------

    private org.springframework.test.web.servlet.ResultActions revert(Cookie who, long meal, long item) throws Exception {
        return mvc.perform(delete("/api/meals/" + meal + "/items/" + item + "/nutrition").cookie(who));
    }

    @Test
    void 직접_적은_값을_지우고_공공_값으로_되돌린다() throws Exception {
        Object[] added = addWithMealTime("{\"foodCode\":\"" + JJOLMYEON + "\",\"servings\":2}");
        postNutrition(access, mealId, (long) added[0],
                        "{\"nutritionOverride\":{\"carbG\":45},\"expectedUpdatedAt\":\"" + added[1] + "\"}")
                .andExpect(status().isOk());

        revert(access, mealId, (long) added[0])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutritionSource").value("DB"))
                .andExpect(jsonPath("$.data.nutritionOverride").value(nullValue()))
                .andExpect(jsonPath("$.data.servings").value(2))                       // 먹은 양은 그대로
                .andExpect(jsonPath("$.data.nutrition.carbG").value(255.9));           // 23.26 × 1100 / 100

        // 이미 공공 값이어도 다시 하면 200
        revert(access, mealId, (long) added[0])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nutritionSource").value("DB"));
    }

    @Test
    void 되돌릴_때는_지금_음식_데이터가_아니라_복사해_둔_값을_쓴다() throws Exception {
        long itemId = addAndGetId(mealId, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        // 나중에 식약처 데이터가 바뀐 상황 (테스트가 끝나면 원래대로)
        jdbc.update("UPDATE foods SET carb_g = 99.99 WHERE food_code = ?", JJOLMYEON);
        try {
            revert(access, mealId, itemId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nutrition.carbG").value(127.9));       // 바뀌기 전 23.26으로 계산
        } finally {
            jdbc.update("UPDATE foods SET carb_g = 23.26 WHERE food_code = ?", JJOLMYEON);
        }
    }

    @Test
    void 직접_적은_음식은_되돌릴_공공_값이_없어서_409() throws Exception {
        long itemId = addAndGetId(mealId, "{\"name\":\"잡채\",\"nutrition\":{\"carbG\":40}}");
        revert(access, mealId, itemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40902"));
    }

    @Test
    void 남의_식사나_지운_항목은_되돌릴_수_없다() throws Exception {
        long itemId = addAndGetId(mealId, "{\"foodCode\":\"" + JJOLMYEON + "\"}");
        revert(other, mealId, itemId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        jdbc.update("UPDATE meal_items SET deleted_at = now() WHERE id = ?", itemId);
        revert(access, mealId, itemId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
    }

    // ---------- 삭제 ----------

    @Test
    void 항목을_지우면_지운_시각만_적히고_다시_지우면_404() throws Exception {
        long itemId = addAndGetId(mealId, "{\"name\":\"떡\"}");
        Instant past = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("UPDATE meals SET updated_at = ? WHERE id = ?", Timestamp.from(past), mealId);

        mvc.perform(delete("/api/meals/" + mealId + "/items/" + itemId).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());   // data가 없으면 칸 자체를 안 보냄

        // 줄은 남고 지운 시각만 적힘, 식사 수정 시각도 바뀜
        Timestamp deletedAt = jdbc.queryForObject("SELECT deleted_at FROM meal_items WHERE id = ?", Timestamp.class, itemId);
        assertThat(deletedAt).isNotNull();
        Timestamp updated = jdbc.queryForObject("SELECT updated_at FROM meals WHERE id = ?", Timestamp.class, mealId);
        assertThat(updated.toInstant()).isAfter(past);

        // 지운 항목은 또 지우거나 고칠 수 없음
        mvc.perform(delete("/api/meals/" + mealId + "/items/" + itemId).cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
        patchItem(access, mealId, itemId, "{\"servings\":2}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
    }

    @Test
    void 남의_항목이나_다른_식사의_항목은_지울_수_없다() throws Exception {
        long itemId = addAndGetId(mealId, "{\"name\":\"떡\"}");
        mvc.perform(delete("/api/meals/" + mealId + "/items/" + itemId).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40401"));
        long otherMeal = createMeal(access);
        mvc.perform(delete("/api/meals/" + otherMeal + "/items/" + itemId).cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEAL_40402"));
        // 그대로 살아 있음
        Timestamp deletedAt = jdbc.queryForObject("SELECT deleted_at FROM meal_items WHERE id = ?", Timestamp.class, itemId);
        assertThat(deletedAt).isNull();
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(delete("/api/meals/" + mealId + "/items/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/meals/" + mealId + "/items")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"떡\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/meals/" + mealId + "/items/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"servings\":2}"))
                .andExpect(status().isUnauthorized());
    }
}
