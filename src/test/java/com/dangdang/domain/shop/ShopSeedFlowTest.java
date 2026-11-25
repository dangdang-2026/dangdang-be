package com.dangdang.domain.shop;

import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 마이그레이션이 넣는 피그마 예시 상품 7개와 그 사진(static/shop)
// 다른 상점 테스트가 products 를 비우므로, 마이그레이션 파일의 예시 상품 부분을 다시 실행해서 본다
class ShopSeedFlowTest extends ReminderTestBase {

    private static final String MIGRATION = "db/migration/V20261125_3__shop.sql";
    private static final String SEED_MARK = "-- ===== 예시 상품 (seed) =====";

    Cookie jisu;

    @BeforeEach
    void seed() throws Exception {
        jisu = login("jisu@x.com");
        jdbc.update("DELETE FROM products");
        String sql = new ClassPathResource(MIGRATION).getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains(SEED_MARK);
        jdbc.execute(sql.substring(sql.indexOf(SEED_MARK)));
    }

    @AfterEach
    void cleanProducts() {
        jdbc.update("DELETE FROM products");
    }

    @Test
    void 예시_상품_7개_PICK은_그릭요거트_하나_판매처는_쇼핑몰별() throws Exception {
        mvc.perform(get("/api/products").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(7)))
                .andExpect(jsonPath("$.data.items[0].name").value("무가당 그릭요거트, 100g, 4개입"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://api.dang-dang.site/shop/greek-yogurt-1.jpg"))
                .andExpect(jsonPath("$.data.items[4].category").value("LUNCHBOX"));
        mvc.perform(get("/api/products").param("pick", "true").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("무가당 그릭요거트, 100g, 4개입"));

        Long yogurt = jdbc.queryForObject("SELECT id FROM products WHERE pick", Long.class);
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))        // 피그마 P-02 「100g당 2,450원 · 무료배송」·「영양 (1개)」
                .andExpect(jsonPath("$.data.pricePer100").value(2450))
                .andExpect(jsonPath("$.data.shippingNote").value("무료배송"))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(90.0))
                .andExpect(jsonPath("$.data.sellerName").value("컬리"));
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://www.kurly.com"));
        assertThat(jdbc.queryForList("SELECT DISTINCT seller_name FROM products", String.class))
                .containsExactlyInAnyOrder("컬리", "널담", "그리팅");

        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))        // P-02 「1 | 3」
                .andExpect(jsonPath("$.data.imageUrls", hasSize(3)))
                .andExpect(jsonPath("$.data.imageUrls[2]").value("https://api.dang-dang.site/shop/greek-yogurt-3.jpg"));

        // 상품마다 사진 3장, 파일이 모두 저장소에 있다 (image_urls 의 파일 이름 = static/shop 파일 이름)
        assertThat(jdbc.queryForList("SELECT jsonb_array_length(image_urls) FROM products", Integer.class))
                .hasSize(7).containsOnly(3);
        for (String url : jdbc.queryForList("SELECT jsonb_array_elements_text(image_urls) FROM products", String.class)) {
            assertThat(url).startsWith("https://api.dang-dang.site/shop/");
            assertThat(new ClassPathResource("static/shop/" + url.substring(url.lastIndexOf('/') + 1)).exists()).as(url).isTrue();
        }
    }

    @Test
    void 상품_사진은_로그인_없이_jpeg로_나간다() throws Exception {
        mvc.perform(get("/shop/greek-yogurt-1.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
        mvc.perform(get("/shop/없는-사진.jpg"))
                .andExpect(status().isNotFound());
    }
}
