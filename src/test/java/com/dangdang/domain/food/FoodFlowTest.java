package com.dangdang.domain.food;

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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 음식 검색 흐름 테스트. foods는 마이그레이션으로 넣은 식약처 데이터(2026-09-29 기준)를 그대로 씀
// 식품코드 두 번째 글자 = 식품기원코드 (D1 가정식, D2 프랜차이즈, D3 외식 …)
@SpringBootTest
@AutoConfigureMockMvc
class FoodFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;

    Cookie access;

    @BeforeEach
    void loginFirst() throws Exception {
        userRepository.save(User.ofEmail("jisu@x.com", "테스트", passwordEncoder.encode("abcd1234"), Instant.now()));
        access = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jisu@x.com\",\"password\":\"abcd1234\"}"))
                .andReturn().getResponse().getCookie("ACCESS_TOKEN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM users");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void 이름이_똑같은_가정식이_먼저_나오고_영양값은_1인분_기준() throws Exception {
        // 쫄면 (가정식): 1인분 550g, 100g당 129kcal · 탄수화물 23.26g · 당류 3.51g
        mvc.perform(get("/api/foods").cookie(access).param("q", "쫄면"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D103-173000000-0001"))
                .andExpect(jsonPath("$.data.items[0].name").value("쫄면"))
                .andExpect(jsonPath("$.data.items[0].origin").value("HOME"))
                .andExpect(jsonPath("$.data.items[0].maker").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].servingG").value(550.0))
                .andExpect(jsonPath("$.data.items[0].servingUnit").value("g"))
                .andExpect(jsonPath("$.data.items[0].kcal").value(709.5))
                .andExpect(jsonPath("$.data.items[0].carbG").value(127.9))
                .andExpect(jsonPath("$.data.items[0].sugarG").value(19.3))
                .andExpect(jsonPath("$.data.items[0].sourceVersion").value("2026-09-29"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void 띄어쓰기는_무시한다() throws Exception {
        mvc.perform(get("/api/foods").cookie(access).param("q", " 쫄 면 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D103-173000000-0001"));
    }

    @Test
    void 순서가_바뀐_식약처_이름도_사람이_부르는_순서로_찾는다() throws Exception {
        // 식약처 이름은 "볶음밥_김치", "김치찌개_참치"처럼 종류_재료 순서
        mvc.perform(get("/api/foods").cookie(access).param("q", "김치볶음밥"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D101-017070000-0001"))
                .andExpect(jsonPath("$.data.items[0].name").value("볶음밥_김치"));
        mvc.perform(get("/api/foods").cookie(access).param("q", "참치 김치찌개"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D106-266250000-0001"))
                .andExpect(jsonPath("$.data.items[0].name").value("김치찌개_참치"));
    }

    @Test
    void 조각이_여러_개면_재료를_적힌_순서대로_붙여도_찾는다() throws Exception {
        // 샌드위치_햄_치즈 → 햄치즈샌드위치, 무국_소고기_두부 → 소고기두부무국, 김밥_참치_김치 → 참치김치김밥
        String[][] cases = {
                {"햄치즈샌드위치", "D302-096434400-0002"},
                {"소고기두부무국", "D605-219241700-0001"},
                {"참치김치김밥", "D401-007455900-0001"},
        };
        for (String[] c : cases) {
            mvc.perform(get("/api/foods").cookie(access).param("q", c[0]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].foodCode", hasItem(c[1])));
        }
    }

    @Test
    void 화면_이름은_사람이_부르는_순서로_준다() throws Exception {
        // {식품코드, 식약처 이름 → 화면 이름}
        String[][] cases = {
                {"D101-017070000-0001", "김치 볶음밥"},                     // 볶음밥_김치
                {"D302-096434400-0002", "햄 치즈 샌드위치"},                // 샌드위치_햄_치즈
                {"D614-637144300-0001", "채소 부추무침"},                   // 부추무침_부추_채소 (종류에 든 "부추"는 뺌)
                {"D102-116190000-0001", "사과파이"},                        // 파이/만주_사과파이 (뒤 조각에 종류가 들어 있음)
                {"D513-615030000-0001", "쑥갓나물 (고춧가루)"},             // 쑥갓나물_고춧가루 (양념은 괄호)
                {"D303-164000000-0001", "쫄우동 (간편조리세트)"},           // 우동_간편조리세트_쫄우동
                {"D201-013000000-0001", "스파이시 씨푸드 리조또"},          // 프랜차이즈는 상품명만
                {"D103-173000000-0001", "쫄면"},                            // _ 없으면 그대로
        };
        for (String[] c : cases) {
            mvc.perform(get("/api/foods/" + c[0]).cookie(access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.displayName").value(c[1]));
        }
        // 원래 이름(name)은 식약처 그대로 남음
        mvc.perform(get("/api/foods/D101-017070000-0001").cookie(access))
                .andExpect(jsonPath("$.data.name").value("볶음밥_김치"));
    }

    @Test
    void 구분자_기호만_검색하면_빈_목록() throws Exception {
        // |는 검색용 이름의 구분자라 검색어에서 빼고, 남는 글자가 없으면 빈 결과
        mvc.perform(get("/api/foods").cookie(access).param("q", "|"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 음료는_ml_단위로_준다() throws Exception {
        // 라떼 (외식): 1인분 500ml, 100ml당 탄수화물 13.26g
        mvc.perform(get("/api/foods").cookie(access).param("q", "라떼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D320-723000000-0001"))
                .andExpect(jsonPath("$.data.items[0].origin").value("RESTAURANT"))
                .andExpect(jsonPath("$.data.items[0].servingUnit").value("ml"))
                .andExpect(jsonPath("$.data.items[0].servingG").value(500.0))
                .andExpect(jsonPath("$.data.items[0].carbG").value(66.3));
    }

    @Test
    void 모르는_영양값은_0이_아니라_null() throws Exception {
        // 프랜차이즈 리조또: 탄수화물 값이 원본에 없음
        mvc.perform(get("/api/foods").cookie(access).param("q", "리소토/리조또_스파이시 씨푸드 리조또"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D201-013000000-0001"))
                .andExpect(jsonPath("$.data.items[0].origin").value("FRANCHISE"))
                .andExpect(jsonPath("$.data.items[0].maker").value("할리스"))
                .andExpect(jsonPath("$.data.items[0].kcal").value(356.7))
                .andExpect(jsonPath("$.data.items[0].carbG").value(nullValue()));
    }

    @Test
    void 일인분_양을_모르면_servingG는_null이고_영양값은_100g당() throws Exception {
        mvc.perform(get("/api/foods").cookie(access).param("q", "수유김밥"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode").value("D301-007000000-0001"))
                .andExpect(jsonPath("$.data.items[0].servingG").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].kcal").value(128.0))
                .andExpect(jsonPath("$.data.items[0].carbG").value(21.4));
    }

    @Test
    void 프랜차이즈는_일반_음식_뒤에_나온다() throws Exception {
        // 피자 검색 결과 중 프랜차이즈가 아닌 음식은 36개 (피자빵 포함). 프랜차이즈 피자 수천 개보다 전부 먼저 나와야 함
        mvc.perform(get("/api/foods").cookie(access).param("q", "피자").param("size", "36"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(36)))
                .andExpect(jsonPath("$.data.items[*].foodCode", everyItem(not(startsWith("D2")))))
                .andExpect(jsonPath("$.data.items[*].name", hasItem("피자빵")))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/foods").cookie(access).param("q", "피자").param("size", "1").param("page", "36"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].foodCode", startsWith("D2")));
    }

    @Test
    void 페이지를_넘기면_다음_결과가_나온다() throws Exception {
        String first = mvc.perform(get("/api/foods").cookie(access).param("q", "김치").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", greaterThan(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String firstCode = com.jayway.jsonpath.JsonPath.read(first, "$.data.items[0].foodCode");
        mvc.perform(get("/api/foods").cookie(access).param("q", "김치").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items[0].foodCode").value(not(firstCode)));
    }

    @Test
    void 결과가_없으면_빈_목록() throws Exception {
        mvc.perform(get("/api/foods").cookie(access).param("q", "없는음식이름xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 검색어의_퍼센트는_글자_그대로_찾는다() throws Exception {
        // %를 LIKE 와일드카드로 두면 전부 걸림. 이름에 %가 든 음식은 없음
        mvc.perform(get("/api/foods").cookie(access).param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 잘못된_값이면_400() throws Exception {
        String[][] cases = {
                {"q", "   "},              // 공백만
                {"q", "가".repeat(101)},    // 101자
        };
        for (String[] c : cases) {
            mvc.perform(get("/api/foods").cookie(access).param(c[0], c[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        String[][] paging = {{"size", "0"}, {"size", "101"}, {"page", "-1"}};
        for (String[] c : paging) {
            mvc.perform(get("/api/foods").cookie(access).param("q", "김치").param(c[0], c[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        }
        mvc.perform(get("/api/foods").cookie(access))   // q 없음
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 상세는_검색_결과와_같은_모양() throws Exception {
        mvc.perform(get("/api/foods/D103-173000000-0001").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.foodCode").value("D103-173000000-0001"))
                .andExpect(jsonPath("$.data.name").value("쫄면"))
                .andExpect(jsonPath("$.data.servingG").value(550.0))
                .andExpect(jsonPath("$.data.servingUnit").value("g"))
                .andExpect(jsonPath("$.data.kcal").value(709.5))
                .andExpect(jsonPath("$.data.carbG").value(127.9))
                .andExpect(jsonPath("$.data.sugarG").value(19.3))
                .andExpect(jsonPath("$.data.sourceVersion").value("2026-09-29"));
        // 모르는 영양값은 상세에서도 null
        mvc.perform(get("/api/foods/D201-013000000-0001").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carbG").value(nullValue()));
    }

    @Test
    void 없는_식품코드면_404() throws Exception {
        mvc.perform(get("/api/foods/D999-000000000-0000").cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40401"));
        // 대소문자도 그대로 비교함 (코드를 바꿔 찾지 않음)
        mvc.perform(get("/api/foods/d103-173000000-0001").cookie(access))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FOOD_40401"));
    }

    @Test
    void 식품코드가_30자를_넘으면_400() throws Exception {
        mvc.perform(get("/api/foods/" + "D".repeat(31)).cookie(access))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/foods").param("q", "김치"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/foods/D103-173000000-0001"))
                .andExpect(status().isUnauthorized());
    }
}
