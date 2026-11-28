package com.dangdang.domain.shop;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.dangdang.support.TestConsents.grantSensitive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 상점 혈당 후기 · 저당 추천 (be #192)
// 후기 = 상점 상품을 고른 식사의 식후 혈당. PRODUCT_REVIEW 동의를 켠 사람 것만, 5명 이상일 때만 숫자
class ProductReviewFlowTest extends ShopTestBase {

    private final List<Long> moms = new ArrayList<>();

    @BeforeEach
    void setUpMoms() {
        moms.clear();
        for (int i = 1; i <= 7; i++) {
            moms.add(grantSensitive(jdbc, userRepository.save(User.ofEmail("mom" + i + "@x.com", "엄마" + i,
                    passwordEncoder.encode("abcd1234"), Instant.now()))).getId());
        }
    }

    @Test
    void 동의한_5명이_모이기_전에는_숫자를_안_주고_모이면_사람_수로_센다() throws Exception {
        for (int i = 0; i < 4; i++) {
            consent(moms.get(i));
            review(moms.get(i), yogurt, 120, 140, 60, NOW.minus(Duration.ofDays(1)));
        }
        review(moms.get(5), yogurt, 120, 140, 60, NOW.minus(Duration.ofDays(1)));   // 동의 안 함 → 안 셈

        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))
                .andExpect(jsonPath("$.data.review.collecting").value(true))
                .andExpect(jsonPath("$.data.review.reviewerCount").value(nullValue()));

        consent(moms.get(4));
        review(moms.get(4), yogurt, 150, 140, 60, NOW.minus(Duration.ofDays(2)));
        // 같은 사람이 또 먹음: 가장 최근 것(목표 안) 하나로 센다
        review(moms.get(4), yogurt, 130, 140, 60, NOW.minus(Duration.ofHours(5)));
        // 목표가 빈 기준의 혈당은 뺀다
        consent(moms.get(6));
        review(moms.get(6), yogurt, 100, null, 60, NOW.minus(Duration.ofDays(1)));

        mvc.perform(get("/api/products").cookie(jisu))
                .andExpect(jsonPath("$.data.items[?(@.id==" + yogurt + ")].review.collecting").value(false))
                .andExpect(jsonPath("$.data.items[?(@.id==" + yogurt + ")].review.reviewerCount").value(5))
                .andExpect(jsonPath("$.data.items[?(@.id==" + yogurt + ")].review.inTargetCount").value(5))
                .andExpect(jsonPath("$.data.items[?(@.id==" + granola + ")].review.collecting").value(true));
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))
                .andExpect(jsonPath("$.data.review.reviewerCount").value(5));
    }

    @Test
    void 동의를_끄면_그_사람_기록은_빠진다() throws Exception {
        fiveReviews(yogurt);
        jdbc.update("INSERT INTO consent_events (user_id, consent_type, document_version, granted, occurred_at)"
                + " VALUES (?, 'PRODUCT_REVIEW', 'v1', false, now())", moms.get(0));

        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))
                .andExpect(jsonPath("$.data.review.collecting").value(true));
    }

    @Test
    void 찜한_상품과_최근_본_상품_카드에도_요약이_붙는다() throws Exception {
        fiveReviews(yogurt);
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu)).andExpect(status().isOk());

        mvc.perform(get("/api/me/liked-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].review.reviewerCount").value(5));
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu)).andExpect(status().isOk());   // 열어야 최근 본 상품에 남음
        mvc.perform(get("/api/me/recent-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].product.review.reviewerCount").value(5));
    }

    @Test
    void 후기_목록은_필터마다_따로_세고_5명이_안_되면_줄도_안_준다() throws Exception {
        for (int i = 0; i < 6; i++) {
            consent(moms.get(i));
            int minutes = i < 5 ? 60 : 120;
            int value = i == 0 ? 150 : 120;
            long meal = review(moms.get(i), yogurt, value, 140, minutes, NOW.minus(Duration.ofHours(i + 1)));
            if (i == 1) {   // 같이 먹은 것: 식약처 음식·상품 이름만, 직접 적은 이름은 안 줌
                item(meal, "D000001", null, "삶은 달걀");
                item(meal, null, null, "엄마가 만든 쿠키");
                item(meal, null, granola, "브랜드 A 저당 그래놀라 300g");
            }
        }
        // 임신 주수: 예정일 2027-02-01 → 2026-11-09 은 28주
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, care_mode) VALUES (?, '2027-02-01', 'DIET')", moms.get(1));
        jdbc.update("INSERT INTO pregnancies (user_id, due_date, care_mode) VALUES (?, '2027-02-01', 'INSULIN')", moms.get(2));

        mvc.perform(get("/api/products/" + yogurt + "/reviews").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.reviewerCount").value(6))
                .andExpect(jsonPath("$.data.summary.inTargetCount").value(5))
                .andExpect(jsonPath("$.data.summary.aboveCount").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(6)))
                .andExpect(jsonPath("$.data.items[0].value").value(150))       // 가장 최근 것 먼저
                .andExpect(jsonPath("$.data.items[0].inTarget").value(false))
                .andExpect(jsonPath("$.data.items[1].pregnancyWeek").value(28))
                .andExpect(jsonPath("$.data.items[1].withFoods", contains("삶은 달걀", "브랜드 A 저당 그래놀라 300g")))
                .andExpect(jsonPath("$.data.items[0].pregnancyWeek").value(nullValue()))
                .andExpect(jsonPath("$.data.items[5].postMealMinutes").value(120))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mvc.perform(get("/api/products/" + yogurt + "/reviews?filter=ONE_HOUR").cookie(jisu))
                .andExpect(jsonPath("$.data.summary.reviewerCount").value(5));
        // 2시간은 1명 → 모으는 중
        mvc.perform(get("/api/products/" + yogurt + "/reviews?filter=TWO_HOUR").cookie(jisu))
                .andExpect(jsonPath("$.data.summary.collecting").value(true))
                .andExpect(jsonPath("$.data.summary.reviewerCount").value(nullValue()))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        // 인슐린 쓰는 1명 빼고 5명
        mvc.perform(get("/api/products/" + yogurt + "/reviews?filter=NO_INSULIN").cookie(jisu))
                .andExpect(jsonPath("$.data.summary.reviewerCount").value(5));
    }

    @Test
    void 후기_목록은_커서로_이어_받고_다른_필터의_커서는_막는다() throws Exception {
        fiveReviews(yogurt);

        String first = mvc.perform(get("/api/products/" + yogurt + "/reviews?size=3").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.data.nextCursor");

        String second = mvc.perform(get("/api/products/" + yogurt + "/reviews?size=3&cursor=" + cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = new ArrayList<>(JsonPath.read(first, "$.data.items[*].id"));
        ids.addAll(JsonPath.read(second, "$.data.items[*].id"));
        assertThat(ids).doesNotHaveDuplicates().hasSize(5);

        mvc.perform(get("/api/products/" + yogurt + "/reviews?filter=ONE_HOUR&cursor=" + cursor).cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40001"));
        mvc.perform(get("/api/products/" + yogurt + "/reviews?cursor=abc").cookie(jisu))
                .andExpect(jsonPath("$.errorCode").value("SHOP_40001"));
        mvc.perform(get("/api/products/" + ended + "/reviews").cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40401"));
    }

    @Test
    void 식사_항목에_상점_상품을_고르면_1개_영양값을_개수만큼_쓴다() throws Exception {
        jdbc.update("UPDATE products SET kcal = 120, carb_g = 6, sugar_g = 4, protein_g = 10 WHERE id = ?", yogurt);
        long meal = meal(jisuId, "SNACK", NOW.minus(Duration.ofHours(2)));

        mvc.perform(post("/api/meals/" + meal + "/items").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + yogurt + ",\"servings\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(yogurt))
                .andExpect(jsonPath("$.data.foodCode").value(nullValue()))
                .andExpect(jsonPath("$.data.name").value("브랜드 B 무가당 그릭요거트, 100g, 4개입"))
                .andExpect(jsonPath("$.data.unitLabel").value("개"))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(240.0))
                .andExpect(jsonPath("$.data.nutrition.sugarG").value(8.0));

        mvc.perform(get("/api/meals/" + meal).cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].productId").value(yogurt))
                .andExpect(jsonPath("$.data.items[0].unitLabel").value("개"));

        // 셋 중 하나만, 무게는 안 됨, 판매 끝난 상품은 404
        mvc.perform(post("/api/meals/" + meal + "/items").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + yogurt + ",\"name\":\"요거트\"}"))
                .andExpect(jsonPath("$.errorCode").value("MEAL_40004"));
        mvc.perform(post("/api/meals/" + meal + "/items").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + yogurt + ",\"weightG\":100}"))
                .andExpect(jsonPath("$.errorCode").value("MEAL_40009"));
        mvc.perform(post("/api/meals/" + meal + "/items").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + ended + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40401"));
    }

    @Test
    void 산모들이_먹고_기록한_간식은_5명_넘게_모인_상품만_사람_많은_순() throws Exception {
        fiveReviews(granola);
        for (int i = 0; i < 6; i++) {
            consent(moms.get(i));
            review(moms.get(i), yogurt, 120, 140, 60, NOW.minus(Duration.ofHours(1)));
        }
        review(moms.get(0), tofu, 120, 140, 60, NOW.minus(Duration.ofHours(1)));   // 1명 → 안 나옴

        mvc.perform(get("/api/products/reviewed").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].id").value(yogurt))
                .andExpect(jsonPath("$.data.items[0].review.reviewerCount").value(6))
                .andExpect(jsonPath("$.data.items[1].id").value(granola));
    }

    @Test
    void 저당_추천은_내가_먹고_높았던_상품을_빼고_후기_좋은_상품부터() throws Exception {
        jdbc.update("UPDATE products SET sugar_g = 3, carb_g = 9 WHERE id = ?", granola);
        jdbc.update("UPDATE products SET sugar_g = 2, carb_g = 5 WHERE id = ?", tofu);      // 밥·면 대신 → 간식이 아니라 뺌
        long sweet = product("SNACK", "브랜드 E", "달콤한 과자", 2000, false, 5, true);
        jdbc.update("UPDATE products SET sugar_g = 20, carb_g = 30 WHERE id = ?", sweet);  // 이유가 없음 → 뺌
        long candy = product("SNACK", "브랜드 F", "저당 젤리", 2500, false, 6, true);
        jdbc.update("UPDATE products SET sugar_g = 1, carb_g = 4 WHERE id = ?", candy);
        fiveReviews(yogurt);                                                                // 5명 모두 목표 안

        // 내 기록: 젤리를 먹고 목표보다 높았음 → 추천에서 뺌. 간식 끼니 하나는 목표 안
        criteria(jisuId, 140, 60);
        long ate = meal(jisuId, "SNACK", NOW.minus(Duration.ofDays(3)));
        item(ate, null, candy, "브랜드 F 저당 젤리");
        reading(jisuId, ate, 160, NOW.minus(Duration.ofDays(3)).plus(Duration.ofHours(1)));
        long snack = meal(jisuId, "SNACK", NOW.minus(Duration.ofDays(2)));
        reading(jisuId, snack, 120, NOW.minus(Duration.ofDays(2)).plus(Duration.ofHours(1)));
        long old = meal(jisuId, "SNACK", NOW.minus(Duration.ofDays(20)));                   // 14일 전보다 오래됨 → 안 셈
        reading(jisuId, old, 170, NOW.minus(Duration.ofDays(20)).plus(Duration.ofHours(1)));

        mvc.perform(get("/api/products/recommended").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basis.days").value(14))
                .andExpect(jsonPath("$.data.basis.snackReadings").value(2))
                .andExpect(jsonPath("$.data.basis.aboveCount").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].product.id").value(yogurt))
                .andExpect(jsonPath("$.data.items[0].reason").value("REVIEWED_IN_TARGET"))
                .andExpect(jsonPath("$.data.items[1].product.id").value(granola))
                .andExpect(jsonPath("$.data.items[1].reason").value("LOW_SUGAR"));
    }

    @Test
    void 건강정보_동의를_철회하면_후기_보태기도_꺼진다() throws Exception {
        mvc.perform(post("/api/me/consents").cookie(jisu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"PRODUCT_REVIEW\",\"granted\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type=='PRODUCT_REVIEW')].granted").value(true));

        mvc.perform(post("/api/me/consents/sensitive/withdraw").cookie(jisu))
                .andExpect(jsonPath("$.data.items[?(@.type=='PRODUCT_REVIEW')].granted").value(false));
    }

    // ── 도우미 ──

    private void fiveReviews(long productId) {
        for (int i = 0; i < 5; i++) {
            consent(moms.get(i));
            review(moms.get(i), productId, 110 + i, 140, 60, NOW.minus(Duration.ofHours(i + 1)));
        }
    }

    private void consent(long userId) {
        jdbc.update("INSERT INTO consent_events (user_id, consent_type, document_version, granted, occurred_at)"
                + " VALUES (?, 'PRODUCT_REVIEW', ?, true, '2026-01-01T00:00:00Z')", userId, ConsentType.PRODUCT_REVIEW.getVersion());
    }

    // 상품을 고른 간식 + 1시간 뒤 식후 혈당. target 이 null 이면 목표가 빈 기준
    private long review(long userId, long productId, int value, Integer target, int minutes, Instant at) {
        long criteria = jdbc.queryForObject("""
                INSERT INTO criteria_versions (user_id, post_meal_minutes, measure_slots, fasting_target, post_meal_target,
                                               effective_from, created_at, auto_empty)
                VALUES (?, ?, '[]'::jsonb, 95, ?, '2026-01-01', now(), false) RETURNING id""", Long.class, userId, minutes, target);
        long meal = meal(userId, "SNACK", at.minus(Duration.ofMinutes(minutes)));
        item(meal, null, productId, "상품");
        jdbc.update("""
                INSERT INTO readings (user_id, meal_id, criteria_version_id, reading_type, slot, measured_at, value,
                                      entered_late, created_at, updated_at, row_version)
                VALUES (?, ?, ?, 'POST_MEAL', NULL, ?, ?, false, now(), now(), 0)""",
                userId, meal, criteria, Timestamp.from(at), value);
        return meal;
    }

    private long criteria(long userId, int target, int minutes) {
        return jdbc.queryForObject("""
                INSERT INTO criteria_versions (user_id, post_meal_minutes, measure_slots, fasting_target, post_meal_target,
                                               effective_from, created_at, auto_empty)
                VALUES (?, ?, '[]'::jsonb, 95, ?, '2026-01-01', now(), false) RETURNING id""", Long.class, userId, minutes, target);
    }

    private void reading(long userId, long mealId, int value, Instant at) {
        Long criteria = jdbc.queryForObject("SELECT max(id) FROM criteria_versions WHERE user_id = ?", Long.class, userId);
        jdbc.update("""
                INSERT INTO readings (user_id, meal_id, criteria_version_id, reading_type, slot, measured_at, value,
                                      entered_late, created_at, updated_at, row_version)
                VALUES (?, ?, ?, 'POST_MEAL', NULL, ?, ?, false, now(), now(), 0)""",
                userId, mealId, criteria, Timestamp.from(at), value);
    }

    private long meal(long userId, String slot, Instant startedAt) {
        return jdbc.queryForObject("""
                INSERT INTO meals (user_id, meal_slot, started_at, started_offline, created_at, updated_at)
                VALUES (?, ?, ?, false, now(), now()) RETURNING id""", Long.class, userId, slot, Timestamp.from(startedAt));
    }

    private void item(long mealId, String foodCode, Long productId, String name) {
        jdbc.update("INSERT INTO meal_items (meal_id, food_code, product_id, name, nutrition_source) VALUES (?, ?, ?, ?, 'DB')",
                mealId, foodCode, productId, name);
    }
}
