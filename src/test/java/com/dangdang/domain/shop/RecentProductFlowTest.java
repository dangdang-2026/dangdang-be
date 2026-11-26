package com.dangdang.domain.shop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 마이 › 최근 본 상품 (상세를 열면 남는다)
class RecentProductFlowTest extends ShopTestBase {

    @Test
    void 다시_보면_맨_앞으로_하트도_같이() throws Exception {
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));
        clock.set(NOW.plusSeconds(60));
        mvc.perform(get("/api/products/" + granola).cookie(jisu));
        clock.set(NOW.plusSeconds(120));
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));      // 다시 봄 → 한 줄, 맨 앞
        mvc.perform(get("/api/products/" + tofu).cookie(minji));       // 민지 것은 안 섞임
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu));

        mvc.perform(get("/api/me/recent-products").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].product.name").value("무가당 그릭요거트, 100g, 4개입"))
                .andExpect(jsonPath("$.data.items[0].product.liked").value(false))
                .andExpect(jsonPath("$.data.items[0].viewedAt").value("2026-11-09T04:02:00Z"))
                .andExpect(jsonPath("$.data.items[1].product.name").value("저당 그래놀라 300g"))
                .andExpect(jsonPath("$.data.items[1].product.liked").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 최근_7일_안_판매중인_것만_커서로_넘긴다() throws Exception {
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));
        clock.set(NOW.plusSeconds(60));
        mvc.perform(get("/api/products/" + granola).cookie(jisu));
        clock.set(NOW.plusSeconds(120));
        mvc.perform(get("/api/products/" + tofu).cookie(jisu));

        String body = mvc.perform(get("/api/me/recent-products").param("size", "2").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.data.nextCursor");
        mvc.perform(get("/api/me/recent-products").param("size", "2").param("cursor", cursor).cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].product.name").value("무가당 그릭요거트, 100g, 4개입"));

        jdbc.update("UPDATE products SET active = false WHERE id = ?", tofu);          // 판매 끝남 → 빠짐
        clock.set(Instant.parse("2026-11-15T14:59:59Z"));                                 // 한국 11/15 23:59:59 → 11/9 본 것도 오늘 포함 7일 안
        mvc.perform(get("/api/me/recent-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].product.name").value("저당 그래놀라 300g"));
        clock.set(Instant.parse("2026-11-15T15:00:00Z"));                                 // 한국 11/16 00:00 → 11/10부터라 다 빠짐
        mvc.perform(get("/api/me/recent-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 최근_7일은_한국_날짜로_6일_전_자정부터() throws Exception {
        clock.set(Instant.parse("2026-11-02T14:59:59Z"));      // 한국 11/2 23:59:59 → 오늘(11/9) 포함 7일 밖
        mvc.perform(get("/api/products/" + granola).cookie(jisu));
        clock.set(Instant.parse("2026-11-02T15:00:00Z"));      // 한국 11/3 00:00 → 딱 7일째라 안
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));

        clock.set(NOW);                                         // 한국 11/9 13:00
        mvc.perform(get("/api/me/recent-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].product.id").value(yogurt));
    }

    @Test
    void 하나씩도_모두도_지우고_남의_것은_남는다() throws Exception {
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));
        mvc.perform(get("/api/products/" + granola).cookie(jisu));
        mvc.perform(get("/api/products/" + granola).cookie(minji));

        mvc.perform(delete("/api/me/recent-products/" + yogurt).cookie(jisu)).andExpect(status().isOk());
        mvc.perform(delete("/api/me/recent-products/" + yogurt).cookie(jisu)).andExpect(status().isOk());
        mvc.perform(get("/api/me/recent-products").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mvc.perform(delete("/api/me/recent-products").cookie(jisu)).andExpect(status().isOk());
        assertThat(count("product_views")).isEqualTo(1);   // 민지 것
    }

    @Test
    void 다른_목록의_커서는_400() throws Exception {
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu));
        String body = mvc.perform(get("/api/me/liked-products").param("size", "1").cookie(jisu))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/me/recent-products").param("cursor", (String) JsonPath.read(body, "$.data.nextCursor"))
                        .cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40001"));
    }
}
