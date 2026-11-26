package com.dangdang.domain.shop;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 상점 상품 목록·상세 (P-01 · P-02)
class ProductFlowTest extends ShopTestBase {

    @Test
    void 목록은_판매중인_것만_운영자_순서대로() throws Exception {
        mvc.perform(get("/api/products").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].name").value("무가당 그릭요거트, 100g, 4개입"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://img.example.com/무가당 그릭요거트, 100g, 4개입-1.png"))
                .andExpect(jsonPath("$.data.items[0].liked").value(false))
                .andExpect(jsonPath("$.data.items[1].affiliate").value(true))
                .andExpect(jsonPath("$.data.items[2].name").value("두부면 비빔면 2인분"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 순서가_같으면_id_순() throws Exception {
        jdbc.update("UPDATE products SET sort_order = 5");
        mvc.perform(get("/api/products").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].id").value(yogurt))
                .andExpect(jsonPath("$.data.items[1].id").value(granola))
                .andExpect(jsonPath("$.data.items[2].id").value(tofu));
    }

    @Test
    void 카테고리_PICK_검색으로_거른다() throws Exception {
        mvc.perform(get("/api/products").param("category", "MEAL_SWAP").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].category").value("MEAL_SWAP"));

        mvc.perform(get("/api/products").param("pick", "true").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].pick").value(true));

        mvc.perform(get("/api/products").param("q", "그래놀라").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        mvc.perform(get("/api/products").param("q", "브랜드 c").cookie(jisu))   // 브랜드도, 대소문자 무시
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("두부면 비빔면 2인분"));

        mvc.perform(get("/api/products").param("q", "끝난").cookie(jisu))        // 내린 상품은 검색에도 없음
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void 검색어의_퍼센트_밑줄은_글자_그대로_찾는다() throws Exception {
        product("SNACK", "브랜드 E", "카카오 70% 다크 초콜릿", 4000, false, 9, true);
        product("SNACK", "브랜드 E", "카카오 70_한정", 4000, false, 10, true);

        mvc.perform(get("/api/products").param("q", "70%").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("카카오 70% 다크 초콜릿"));
        mvc.perform(get("/api/products").param("q", "%").cookie(jisu))        // 전부가 아니라 % 가 든 것만
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mvc.perform(get("/api/products").param("q", "_").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("카카오 70_한정"));
    }

    @Test
    void 페이지를_나눠_준다() throws Exception {
        mvc.perform(get("/api/products").param("size", "2").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/products").param("size", "2").param("page", "1").cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("두부면 비빔면 2인분"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 없는_카테고리나_범위밖_값은_400() throws Exception {
        mvc.perform(get("/api/products").param("category", "CANDY").cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_40001"));
        mvc.perform(get("/api/products").param("size", "51").cookie(jisu))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("page", "10001").cookie(jisu))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("q", "가".repeat(101)).cookie(jisu))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세는_사진들과_판매처_이름만_주고_주소는_안_준다_판매끝난_상품은_404() throws Exception {
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls", hasSize(2)))
                .andExpect(jsonPath("$.data.sellerName").value("○○몰"))
                .andExpect(jsonPath("$.data.sellerUrl").doesNotExist())
                .andExpect(jsonPath("$.data.pick").value(true))
                .andExpect(jsonPath("$.data.liked").value(false));

        mvc.perform(get("/api/products/" + ended).cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40401"));
        mvc.perform(get("/api/products/999999").cookie(jisu))
                .andExpect(status().isNotFound());
    }

    @Test
    void 상세는_100g당_가격_배송_영양을_주고_모르면_null() throws Exception {
        jdbc.update("""
                UPDATE products SET content_amount = 400, content_unit = 'g', shipping_note = '무료배송',
                       serving_label = '1개', carb_g = 5, sugar_g = 4, protein_g = 10, kcal = 90
                WHERE id = ?
                """, yogurt);

        mvc.perform(get("/api/products/" + yogurt).cookie(jisu))
                .andExpect(jsonPath("$.data.price").value(9800))
                .andExpect(jsonPath("$.data.pricePer100").value(2450))          // 9,800원 ÷ 400g × 100
                .andExpect(jsonPath("$.data.contentUnit").value("g"))
                .andExpect(jsonPath("$.data.shippingNote").value("무료배송"))
                .andExpect(jsonPath("$.data.nutrition.servingLabel").value("1개"))
                .andExpect(jsonPath("$.data.nutrition.carbG").value(5.0))
                .andExpect(jsonPath("$.data.nutrition.sugarG").value(4.0))
                .andExpect(jsonPath("$.data.nutrition.proteinG").value(10.0))
                .andExpect(jsonPath("$.data.nutrition.kcal").value(90.0));

        mvc.perform(get("/api/products/" + granola).cookie(jisu))           // 운영자가 안 넣은 상품
                .andExpect(jsonPath("$.data.pricePer100").value(nullValue()))
                .andExpect(jsonPath("$.data.contentUnit").value(nullValue()))
                .andExpect(jsonPath("$.data.shippingNote").value(nullValue()))
                .andExpect(jsonPath("$.data.nutrition").value(nullValue()));

        // 숫자만 있고 단위가 없으면 DB가 막음 (「100?당」이 화면에 나가지 않게)
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET content_amount = 300 WHERE id = ?", granola))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    // null·숫자는 DB CHECK 가 막는다 (ShopSeedFlowTest)
    void 사진_칸에_빈_글자가_섞여도_빼고_준다() throws Exception {
        jdbc.update("UPDATE products SET image_urls = '[\"\", \"  \", \"https://img.example.com/ok.png\"]'::jsonb WHERE id = ?", granola);

        mvc.perform(get("/api/products/" + granola).cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls", hasSize(1)));
        mvc.perform(get("/api/products").param("q", "그래놀라").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://img.example.com/ok.png"));

        jdbc.update("UPDATE products SET image_urls = '[]'::jsonb WHERE id = ?", granola);
        mvc.perform(get("/api/products").param("q", "그래놀라").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value(nullValue()));
    }

    @Test
    void 판매처_주소는_https와_호스트가_있어야_DB에_들어간다() {
        for (String bad : new String[]{"javascript:alert(1)", "http://shop.example.com/1", "https:///evil",
                "https://", " https://shop.example.com", "data:text/html,hi"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE products SET seller_url = ? WHERE id = ?", bad, yogurt))
                    .as(bad).isInstanceOf(DataIntegrityViolationException.class);
        }
        for (String ok : new String[]{"https://shop.example.com", "https://shop.example.com:8443/goods?id=1",
                "https://m.shop.example.com/goods/1#top"}) {
            jdbc.update("UPDATE products SET seller_url = ? WHERE id = ?", ok, yogurt);
        }
    }

    @Test
    void 상세를_열면_최근_본_상품에_한_줄만_남고_시각은_앱_시계() throws Exception {
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu)).andExpect(status().isOk());
        clock.set(NOW.plusSeconds(60));
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_views WHERE user_id = ?", Integer.class, jisuId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT viewed_at FROM product_views WHERE user_id = ?", java.sql.Timestamp.class,
                jisuId).toInstant()).isEqualTo(NOW.plusSeconds(60));

        mvc.perform(get("/api/products/" + ended).cookie(jisu));               // 404 상품은 안 남김
        assertThat(count("product_views")).isEqualTo(1);
    }

    @Test
    void 늦게_도착한_옛_시각은_최근_본_시각을_덮지_않는다() throws Exception {
        clock.set(NOW.plusSeconds(120));
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));
        clock.set(NOW.plusSeconds(60));                         // 먼저 출발했지만 늦게 도착한 요청
        mvc.perform(get("/api/products/" + yogurt).cookie(jisu));

        assertThat(jdbc.queryForObject("SELECT viewed_at FROM product_views WHERE user_id = ?", java.sql.Timestamp.class,
                jisuId).toInstant()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void 상세를_동시에_두_번_열어도_최근_본_상품은_한_줄() throws Exception {
        assertThat(twoAtOnce(() -> mvc.perform(get("/api/products/" + tofu).cookie(jisu))
                .andReturn().getResponse().getStatus())).containsExactly(200, 200);
        assertThat(count("product_views")).isEqualTo(1);
    }

    @Test
    void 로그인_안_하면_401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/" + yogurt)).andExpect(status().isUnauthorized());
    }
}
