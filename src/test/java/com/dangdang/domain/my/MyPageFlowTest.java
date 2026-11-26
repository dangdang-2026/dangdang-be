package com.dangdang.domain.my;

import com.dangdang.domain.auth.infra.kakao.KakaoClient;
import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import com.dangdang.support.ReminderTestBase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 마이: 내 음식 (M-13·M-17·M-18) · 내 식사 안내 (M-15) · 식사 기록 설정 (M-20) · 탈퇴 (M-08)
class MyPageFlowTest extends ReminderTestBase {

    @MockitoBean KakaoClient kakaoClient;   // 진짜 카카오 서버 대신

    static final String REDIRECT = "http://localhost:5173/auth/kakao/callback";   // application.yaml redirect-uris 첫 줄

    Cookie jisu;
    Cookie minji;

    @BeforeEach
    void setUp() throws Exception {
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
    }

    @AfterEach
    void cleanPosts() {   // 「글 남기고 탈퇴」한 글은 사람이 없어도 남아 있어서 따로 지운다
        jdbc.update("DELETE FROM posts");
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               Cookie who, String body) throws Exception {
        return mvc.perform(req.cookie(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long addFood(Cookie who, String body) throws Exception {
        String res = json(post("/api/me/foods"), who, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.id")).longValue();
    }

    // ── 내 음식 ──

    @Test
    void 내_음식_추가_목록_검색() throws Exception {
        addFood(jisu, "{\"name\":\"우리 집 김치볶음밥\",\"similarFoodCode\":\"D101-017070000-0001\",\"usualQuantity\":\"ONE\","
                + "\"nutritionOverride\":{\"carbG\":45,\"proteinG\":null}}");
        clock.set(NOW.plusSeconds(60));
        addFood(jisu, "{\"name\":\"엄마표 잡곡밥\"}");
        addFood(minji, "{\"name\":\"민지 볶음밥\"}");

        mvc.perform(get("/api/me/foods").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("엄마표 잡곡밥"))         // 최근에 고친 순
                .andExpect(jsonPath("$.data[1].usualQuantity").value("ONE"))
                .andExpect(jsonPath("$.data[1].nutritionOverride.carbG").value(45))
                .andExpect(jsonPath("$.data[1].nutritionOverride.proteinG").value(nullValue()))
                .andExpect(jsonPath("$.data[0].isStaple").value(false))   // 비슷한 음식이 없으면 서버가 분류를 모름
                .andExpect(jsonPath("$.data[1].isStaple").value(true));   // 비슷한 음식 볶음밥_김치(밥류)를 따름
        mvc.perform(get("/api/me/foods").param("q", "볶음").cookie(jisu))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("우리 집 김치볶음밥"));
    }

    @Test
    void 없는_비슷한_음식_코드는_404() throws Exception {
        json(post("/api/me/foods"), jisu, "{\"name\":\"잡곡밥\",\"similarFoodCode\":\"NO-SUCH-CODE\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40402"));
        Long id = addFood(jisu, "{\"name\":\"잡곡밥\"}");
        json(patch("/api/me/foods/" + id), jisu, "{\"similarFoodCode\":\"NO-SUCH-CODE\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 같은_이름은_409_빈_이름은_400_빈_영양값은_400() throws Exception {
        addFood(jisu, "{\"name\":\"잡곡밥\"}");
        json(post("/api/me/foods"), jisu, "{\"name\":\" 잡곡밥 \"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40901"));
        json(post("/api/me/foods"), jisu, "{\"name\":\" \"}").andExpect(status().isBadRequest());
        json(post("/api/me/foods"), jisu, "{\"name\":\"현미밥\",\"nutritionOverride\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40002"));
        json(post("/api/me/foods"), jisu, "{\"name\":\"현미밥\",\"nutritionOverride\":{\"carbG\":-1}}")
                .andExpect(status().isBadRequest());
        json(post("/api/me/foods"), minji, "{\"name\":\"잡곡밥\"}").andExpect(status().isCreated());   // 다른 사람은 같은 이름 OK
    }

    @Test
    void 고치기는_보낸_칸만_clear로_비우기() throws Exception {
        Long id = addFood(jisu, "{\"name\":\"잡곡밥\",\"usualQuantity\":\"HALF\",\"nutritionOverride\":{\"kcal\":300}}");

        json(patch("/api/me/foods/" + id), jisu, "{\"name\":\"현미 잡곡밥\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("현미 잡곡밥"))
                .andExpect(jsonPath("$.data.usualQuantity").value("HALF"))
                .andExpect(jsonPath("$.data.nutritionOverride.kcal").value(300));
        json(patch("/api/me/foods/" + id), jisu, "{\"clear\":[\"nutritionOverride\",\"usualQuantity\"]}")
                .andExpect(jsonPath("$.data.nutritionOverride").value(nullValue()))
                .andExpect(jsonPath("$.data.usualQuantity").value(nullValue()));
        json(patch("/api/me/foods/" + id), jisu, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40001"));
        json(patch("/api/me/foods/" + id), jisu, "{\"clear\":[\"name\"]}").andExpect(status().isBadRequest());
    }

    @Test
    void 같은_칸에_값과_clear를_같이_보내면_400() throws Exception {
        Long id = addFood(jisu, "{\"name\":\"잡곡밥\"}");
        // 없는 음식 코드라도 404가 아니라 400 (값과 비우기 중 무엇을 원하는지 알 수 없음)
        json(patch("/api/me/foods/" + id), jisu, "{\"similarFoodCode\":\"NO-SUCH\",\"clear\":[\"similarFoodCode\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40003"));
        json(patch("/api/me/foods/" + id), jisu, "{\"usualGrams\":100,\"clear\":[\"usualGrams\"]}")
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40003"));
    }

    @Test
    void 같은_이름은_DB도_막는다_동시_요청_대비() {
        jdbc.update("INSERT INTO my_foods (user_id, name) VALUES (?, '잡곡밥')", jisuId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbc.update("INSERT INTO my_foods (user_id, name) VALUES (?, '잡곡밥')", jisuId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void 모르는_칸이_저장된_영양값도_읽힌다() throws Exception {
        Long id = addFood(jisu, "{\"name\":\"잡곡밥\",\"nutritionOverride\":{\"kcal\":300}}");
        jdbc.update("UPDATE my_foods SET nutrition_override = '{\"kcal\":300,\"fiberG\":3}'::jsonb WHERE id = ?", id);
        mvc.perform(get("/api/me/foods").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nutritionOverride.kcal").value(300));
    }

    @Test
    void 보통_먹는_그램은_1에서_2000_고치고_비우기() throws Exception {
        Long id = addFood(jisu, "{\"name\":\"우리 집 김치볶음밥\",\"usualGrams\":250}");
        mvc.perform(get("/api/me/foods").cookie(jisu)).andExpect(jsonPath("$.data[0].usualGrams").value(250));

        json(patch("/api/me/foods/" + id), jisu, "{\"usualGrams\":180}")
                .andExpect(jsonPath("$.data.usualGrams").value(180));
        json(patch("/api/me/foods/" + id), jisu, "{\"clear\":[\"usualGrams\"]}")
                .andExpect(jsonPath("$.data.usualGrams").value(nullValue()));
        json(patch("/api/me/foods/" + id), jisu, "{\"usualGrams\":0}").andExpect(status().isBadRequest());
        json(post("/api/me/foods"), jisu, "{\"name\":\"큰 접시\",\"usualGrams\":2001}").andExpect(status().isBadRequest());
    }

    @Test
    void 지우기와_남의_음식은_404() throws Exception {
        Long id = addFood(jisu, "{\"name\":\"잡곡밥\"}");
        mvc.perform(delete("/api/me/foods/" + id).cookie(minji))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MYFOOD_40401"));
        mvc.perform(delete("/api/me/foods/" + id).cookie(jisu)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM my_foods", Integer.class)).isZero();
    }

    // ── 내 식사 안내 ──

    @Test
    void 식사_안내는_없으면_null_저장하면_한_장() throws Exception {
        mvc.perform(get("/api/me/meal-plan").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());   // data가 null이면 칸 자체가 빠진다 (BaseResponse NON_NULL)

        json(put("/api/me/meal-plan"), jisu, "{\"receivedOn\":\"2026-11-05\",\"mealGuide\":\"아침: 잡곡밥 2/3공기\","
                + "\"snackGuide\":\"오후: 우유 1팩\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealGuide").value("아침: 잡곡밥 2/3공기"));
        json(put("/api/me/meal-plan"), jisu, "{\"mealGuide\":\"고친 안내\"}")
                .andExpect(jsonPath("$.data.mealGuide").value("고친 안내"))
                .andExpect(jsonPath("$.data.snackGuide").value(nullValue()))       // 통째로 바꿈
                .andExpect(jsonPath("$.data.receivedOn").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plans", Integer.class)).isEqualTo(1);

        mvc.perform(delete("/api/me/meal-plan").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/me/meal-plan").cookie(jisu)).andExpect(jsonPath("$.data").doesNotExist());   // data가 null이면 칸 자체가 빠진다 (BaseResponse NON_NULL)
    }

    @Test
    void 안내가_둘_다_비면_400_남의_병원이면_400() throws Exception {
        json(put("/api/me/meal-plan"), jisu, "{\"mealGuide\":\" \",\"snackGuide\":null}").andExpect(status().isBadRequest());
        Long minjiSite = jdbc.queryForObject("INSERT INTO care_sites (user_id, name, department) VALUES (?, '민지 병원', '산부인과') RETURNING id",
                Long.class, minjiId);
        json(put("/api/me/meal-plan"), jisu, "{\"careSiteId\":" + minjiSite + ",\"mealGuide\":\"안내\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 받은_병원을_지우면_식사_안내의_병원은_null() throws Exception {
        Long site = jdbc.queryForObject("INSERT INTO care_sites (user_id, name, department) VALUES (?, '지수 병원', 'OBGYN') RETURNING id",
                Long.class, jisuId);
        json(put("/api/me/meal-plan"), jisu, "{\"careSiteId\":" + site + ",\"mealGuide\":\"안내\"}")
                .andExpect(jsonPath("$.data.careSiteId").value(site))
                .andExpect(jsonPath("$.data.careSiteName").value("지수 병원"));
        mvc.perform(delete("/api/care-sites/" + site).cookie(jisu)).andExpect(status().isOk());   // soft delete
        mvc.perform(get("/api/me/meal-plan").cookie(jisu))
                .andExpect(jsonPath("$.data.mealGuide").value("안내"))
                .andExpect(jsonPath("$.data.careSiteId").doesNotExist())
                .andExpect(jsonPath("$.data.careSiteName").value("지수 병원"));   // C-20 「받은 곳은 그대로」
    }

    @Test
    void 받은_곳은_병원_번호와_글을_둘_다_적을_수_있고_빈_글은_null() throws Exception {
        Long site = jdbc.queryForObject("INSERT INTO care_sites (user_id, name, department) VALUES (?, '지수 병원', 'OBGYN') RETURNING id",
                Long.class, jisuId);
        json(put("/api/me/meal-plan"), jisu, "{\"careSiteId\":" + site + ",\"placeName\":\"  보건소 영양 교실 \",\"mealGuide\":\"안내\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careSiteId").value(site))
                .andExpect(jsonPath("$.data.careSiteName").value("지수 병원"))
                .andExpect(jsonPath("$.data.placeName").value("보건소 영양 교실"));   // 앞뒤 공백 지움

        json(put("/api/me/meal-plan"), jisu, "{\"placeName\":\"보건소\",\"mealGuide\":\"안내\"}")   // 병원 없이 글만
                .andExpect(jsonPath("$.data.careSiteId").doesNotExist())
                .andExpect(jsonPath("$.data.placeName").value("보건소"));
        mvc.perform(get("/api/me/meal-plan").cookie(jisu)).andExpect(jsonPath("$.data.placeName").value("보건소"));

        json(put("/api/me/meal-plan"), jisu, "{\"placeName\":\"   \",\"mealGuide\":\"안내\"}")   // 빈 글이면 지움
                .andExpect(jsonPath("$.data.placeName").doesNotExist());
        json(put("/api/me/meal-plan"), jisu, "{\"placeName\":\"" + "가".repeat(51) + "\",\"mealGuide\":\"안내\"}")
                .andExpect(status().isBadRequest());

        // 글자 수는 앞뒤 공백을 지운 뒤 글자(코드 포인트)로 셈 → 이모지가 섞인 50자, 공백 붙은 50자는 됨
        String emoji50 = "🏥".repeat(25) + "가".repeat(25);
        json(put("/api/me/meal-plan"), jisu, "{\"placeName\":\"" + emoji50 + "\",\"mealGuide\":\"안내\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeName").value(emoji50));
        json(put("/api/me/meal-plan"), jisu, "{\"placeName\":\"  " + "가".repeat(50) + "  \",\"mealGuide\":\"안내\"}")
                .andExpect(status().isOk());
        json(put("/api/me/meal-plan"), jisu, "{\"placeName\":\"보건소\\n영양 교실\",\"mealGuide\":\"안내\"}")   // 한 줄만
                .andExpect(status().isBadRequest());
    }

    // ── 식사 기록 설정 (M-20) ──

    @Test
    void 양_적는_방식은_기본_인분_그램으로_바꾸면_나만_바뀐다() throws Exception {
        mvc.perform(get("/api/me/meal-settings").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portionUnit").value("SERVING"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_settings", Integer.class)).isZero();   // 보기만 하면 줄 안 만듦

        json(patch("/api/me/meal-settings"), jisu, "{\"portionUnit\":\"GRAM\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portionUnit").value("GRAM"));
        mvc.perform(get("/api/me/meal-settings").cookie(jisu)).andExpect(jsonPath("$.data.portionUnit").value("GRAM"));
        mvc.perform(get("/api/me/meal-settings").cookie(minji)).andExpect(jsonPath("$.data.portionUnit").value("SERVING"));

        json(patch("/api/me/meal-settings"), jisu, "{\"portionUnit\":\"CUP\"}").andExpect(status().isBadRequest());
        json(patch("/api/me/meal-settings"), jisu, "{}").andExpect(status().isBadRequest());
    }

    // ── 탈퇴 ──

    private String profileAndPost(Cookie who, String nickname) throws Exception {
        json(post("/api/community/profiles"), who, "{\"nickname\":\"" + nickname + "\"}").andExpect(status().isCreated());
        String res = json(post("/api/posts"), who, "{\"category\":\"MEASUREMENT\",\"title\":\"제목\",\"content\":\"본문\"}")
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data.id");
    }

    @Test
    void 탈퇴하면_내_기록이_모두_지워지고_쿠키도_지운다() throws Exception {
        addFood(jisu, "{\"name\":\"잡곡밥\"}");
        json(put("/api/me/meal-plan"), jisu, "{\"mealGuide\":\"안내\"}");
        json(patch("/api/me/meal-settings"), jisu, "{\"portionUnit\":\"GRAM\"}");
        json(post("/api/readings"), jisu, "{\"value\":92,\"measuredAt\":\"2026-11-01T07:00:00+09:00\",\"readingType\":\"FASTING\"}");

        json(post("/api/me/withdrawal"), jisu, "{\"password\":\"abcd1234\"}")
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("ACCESS_TOKEN", 0))
                .andExpect(cookie().maxAge("REFRESH_TOKEN", 0));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, jisuId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM my_foods", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plans", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_settings", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM readings", Integer.class)).isZero();
        mvc.perform(get("/api/me").cookie(jisu)).andExpect(status().is4xxClientError());   // 남은 토큰으로도 안 됨
    }

    @Test
    void 비밀번호가_틀리면_400이고_아무것도_안_지운다() throws Exception {
        json(post("/api/me/withdrawal"), jisu, "{\"password\":\"wrong\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40003"));
        json(post("/api/me/withdrawal"), jisu, "{}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, jisuId)).isEqualTo(1);
    }

    @Test
    void 기본은_내_글을_지우고_남의_글에_단_댓글은_비운다() throws Exception {
        String myPost = profileAndPost(jisu, "말랑한 두부");
        String herPost = profileAndPost(minji, "포근한 고구마");
        json(post("/api/posts/" + myPost + "/comments"), minji, "{\"content\":\"민지 댓글\"}");
        json(post("/api/posts/" + herPost + "/comments"), jisu, "{\"content\":\"지수 댓글\"}");

        json(post("/api/me/withdrawal"), jisu, "{\"password\":\"abcd1234\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts WHERE id = ?", Integer.class, Long.valueOf(myPost))).isZero();
        assertThat(jdbc.queryForObject("SELECT content FROM comments WHERE post_id = ?", String.class, Long.valueOf(herPost)))
                .isEmpty();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM comments WHERE post_id = ?", java.sql.Timestamp.class,
                Long.valueOf(herPost)).toInstant()).isEqualTo(clock.instant());   // 앱 Clock 시각
    }

    @Test
    void 탈퇴_전에_직접_지운_댓글도_내용을_비운다() throws Exception {
        String herPost = profileAndPost(minji, "포근한 고구마");
        json(post("/api/community/profiles"), jisu, "{\"nickname\":\"말랑한 두부\"}").andExpect(status().isCreated());
        String res = json(post("/api/posts/" + herPost + "/comments"), jisu, "{\"content\":\"지울 댓글\"}")
                .andReturn().getResponse().getContentAsString();
        Object commentId = JsonPath.read(res, "$.data.id");
        mvc.perform(delete("/api/comments/" + commentId).cookie(jisu)).andExpect(status().isOk());

        json(post("/api/me/withdrawal"), jisu, "{\"password\":\"abcd1234\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT content FROM comments WHERE post_id = ?", String.class, Long.valueOf(herPost)))
                .isEmpty();
    }

    @Test
    void 글을_남기면_작성자가_탈퇴한_회원으로_보인다() throws Exception {
        String myPost = profileAndPost(jisu, "말랑한 두부");
        json(post("/api/community/profiles"), minji, "{\"nickname\":\"포근한 고구마\"}");

        json(post("/api/me/withdrawal"), jisu, "{\"password\":\"abcd1234\",\"keepPosts\":true}").andExpect(status().isOk());

        mvc.perform(get("/api/posts/" + myPost).cookie(minji))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.displayName").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.data.content").value("본문"));
    }

    @Test
    void 카카오_가입자는_카카오로_다시_확인해야_탈퇴() throws Exception {
        jdbc.update("UPDATE users SET auth_provider = 'KAKAO', provider_subject = '12345', password_hash = NULL WHERE id = ?", jisuId);
        given(kakaoClient.fetchUser("ok-code", REDIRECT)).willReturn(new KakaoUser("12345", null, "지수"));

        json(post("/api/me/withdrawal"), jisu, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40004"));
        json(post("/api/me/withdrawal"), jisu, "{\"kakaoCode\":\"ok-code\",\"redirectUri\":\"\"}")   // 빈 주소도 다시 확인 (리뷰 #128)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("USER_40004"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, jisuId)).isOne();

        json(post("/api/me/withdrawal"), jisu, "{\"kakaoCode\":\"ok-code\",\"redirectUri\":\"" + REDIRECT + "\"}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, jisuId)).isZero();
    }

    @Test
    void 다른_카카오_계정이나_허용_안_된_주소면_탈퇴_안_됨() throws Exception {
        jdbc.update("UPDATE users SET auth_provider = 'KAKAO', provider_subject = '12345', password_hash = NULL WHERE id = ?", jisuId);
        given(kakaoClient.fetchUser("other-code", REDIRECT)).willReturn(new KakaoUser("99999", null, "남"));

        json(post("/api/me/withdrawal"), jisu, "{\"kakaoCode\":\"other-code\",\"redirectUri\":\"" + REDIRECT + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_40301"));
        json(post("/api/me/withdrawal"), jisu, "{\"kakaoCode\":\"ok-code\",\"redirectUri\":\"https://evil.example/cb\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_40007"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, jisuId)).isOne();
    }
}
