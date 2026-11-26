package com.dangdang.domain.shop;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 마이 › 쇼핑 메뉴 숫자 (M-01 v2) + 탈퇴하면 상점 기록도 같이 지워지는지
class ShoppingSummaryFlowTest extends ShopTestBase {

    @Test
    void 메뉴_숫자는_판매중인_찜_수와_최근_7일_본_수() throws Exception {
        mvc.perform(get("/api/me/shopping-summary").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likedCount").value(0))
                .andExpect(jsonPath("$.data.recentCount").value(0));

        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + tofu + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + tofu + "/like").cookie(minji));      // 민지 것은 안 셈
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));
        clock.set(NOW.plusSeconds(3 * 24 * 3600));
        mvc.perform(get("/api/products/" + granola).cookie(jisu));
        jdbc.update("UPDATE products SET active = false WHERE id = ?", tofu);

        mvc.perform(get("/api/me/shopping-summary").cookie(jisu))
                .andExpect(jsonPath("$.data.likedCount").value(2))
                .andExpect(jsonPath("$.data.recentCount").value(2));
        mvc.perform(get("/api/me/liked-products").cookie(jisu))                 // 찜한 상품 화면 숫자와 같음
                .andExpect(jsonPath("$.data.totalCount").value(2));

        clock.set(NOW.plusSeconds(8 * 24 * 3600));   // 요거트 본 지 8일
        mvc.perform(get("/api/me/shopping-summary").cookie(jisu))
                .andExpect(jsonPath("$.data.recentCount").value(1));
    }

    @Test
    void 최근_본_수도_목록과_같은_한국_날짜_7일_기준() throws Exception {
        clock.set(Instant.parse("2026-11-02T14:59:59Z"));      // 한국 11/2 23:59:59
        mvc.perform(get("/api/products/" + granola).cookie(jisu));
        clock.set(Instant.parse("2026-11-02T15:00:00Z"));      // 한국 11/3 00:00
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));

        clock.set(Instant.parse("2026-11-09T14:59:59Z"));      // 한국 11/9 23:59:59 → 11/3부터
        mvc.perform(get("/api/me/shopping-summary").cookie(jisu))
                .andExpect(jsonPath("$.data.recentCount").value(1));
        clock.set(Instant.parse("2026-11-09T15:00:00Z"));      // 한국 11/10 00:00 → 11/4부터
        mvc.perform(get("/api/me/shopping-summary").cookie(jisu))
                .andExpect(jsonPath("$.data.recentCount").value(0));
    }

    @Test
    void 탈퇴하면_찜_간_기록_최근_본_상품이_같이_지워진다() throws Exception {
        for (var cookie : new jakarta.servlet.http.Cookie[]{jisu, minji}) {
            mvc.perform(put("/api/products/" + yogurt + "/like").cookie(cookie));
            mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(cookie));
            mvc.perform(get("/api/products/" + yogurt).cookie(cookie));
        }

        mvc.perform(post("/api/me/withdrawal").cookie(jisu)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"abcd1234\"}"))
                .andExpect(status().isOk());

        for (String table : new String[]{"product_likes", "product_visits", "product_views"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, jisuId))
                    .as(table).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, minjiId))
                    .as(table).isEqualTo(1);
        }
    }
}
