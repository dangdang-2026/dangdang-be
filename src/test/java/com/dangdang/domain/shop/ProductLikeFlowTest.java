package com.dangdang.domain.shop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 찜 (하트 · M-21 찜한 상품)
class ProductLikeFlowTest extends ShopTestBase {

    @Test
    void 찜하면_목록과_상세에_하트가_사람마다_다르게() throws Exception {
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu)).andExpect(status().isOk());

        mvc.perform(get("/api/products").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].liked").value(false))
                .andExpect(jsonPath("$.data.items[1].liked").value(true));
        mvc.perform(get("/api/products/" + granola).cookie(jisu))
                .andExpect(jsonPath("$.data.liked").value(true));
        mvc.perform(get("/api/products").cookie(minji))                        // 민지에겐 하트 없음
                .andExpect(jsonPath("$.data.items[1].liked").value(false));
    }

    @Test
    void 찜은_두_번_눌러도_한_줄_빼기도_여러_번_200() throws Exception {
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu)).andExpect(status().isOk());
        assertThat(count("product_likes")).isEqualTo(1);

        mvc.perform(delete("/api/products/" + yogurt + "/like").cookie(jisu)).andExpect(status().isOk());
        mvc.perform(delete("/api/products/" + yogurt + "/like").cookie(jisu)).andExpect(status().isOk());
        assertThat(count("product_likes")).isZero();

        mvc.perform(put("/api/products/" + ended + "/like").cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40401"));
        mvc.perform(delete("/api/products/" + ended + "/like").cookie(jisu)).andExpect(status().isOk());
    }

    @Test
    void 찜을_동시에_두_번_눌러도_둘_다_200이고_한_줄() throws Exception {
        assertThat(twoAtOnce(() -> mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu))
                .andReturn().getResponse().getStatus())).containsExactly(200, 200);
        assertThat(count("product_likes")).isEqualTo(1);
    }

    @Test
    void 찜한_상품은_최근_것_먼저_커서로_넘기고_판매끝난_건_목록과_숫자에서_빠진다() throws Exception {
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu));
        clock.set(NOW.plusSeconds(60));
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu));
        clock.set(NOW.plusSeconds(120));
        mvc.perform(put("/api/products/" + tofu + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + tofu + "/like").cookie(minji));   // 민지 것은 안 보여야 함

        String body = mvc.perform(get("/api/me/liked-products").param("size", "2").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].name").value("두부면 비빔면 2인분"))
                .andExpect(jsonPath("$.data.items[0].liked").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(3))                // 화면 위 「3개」 — 페이지가 아니라 전체
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.data.nextCursor");

        mvc.perform(get("/api/me/liked-products").param("size", "2").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("무가당 그릭요거트, 100g, 4개입"))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));

        jdbc.update("UPDATE products SET active = false WHERE id = ?", granola);
        mvc.perform(get("/api/me/liked-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalCount").value(2));               // 숫자도 목록과 같은 조건
    }

    @Test
    void 같은_시각에_찜해도_커서가_빠뜨리거나_겹치지_않는다() throws Exception {
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + tofu + "/like").cookie(jisu));      // 셋 다 NOW

        String first = mvc.perform(get("/api/me/liked-products").param("size", "2").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].id").value(tofu))
                .andExpect(jsonPath("$.data.items[1].id").value(granola))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/me/liked-products").param("size", "2")
                        .param("cursor", (String) JsonPath.read(first, "$.data.nextCursor")).cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(yogurt));
    }

    @Test
    void 이상한_커서는_400() throws Exception {
        mvc.perform(get("/api/me/liked-products").param("cursor", "abc").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40001"));
    }
}
