package com.dangdang.domain.shop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 판매처로 이동 (P-06) + 판매처로 간 기록 (M-22)
class ProductVisitFlowTest extends ShopTestBase {

    @Autowired PlatformTransactionManager txManager;

    @Test
    void 구매하기는_기록을_남기고_판매처_주소를_준다_10분_안엔_한_줄() throws Exception {
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://shop.example.com/goods/" + yogurt));

        clock.set(NOW.plusSeconds(5 * 60));
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu)).andExpect(status().isOk());
        assertThat(count("product_visits")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT visited_at FROM product_visits", java.sql.Timestamp.class).toInstant())
                .isEqualTo(NOW.plusSeconds(5 * 60));                                  // 합치면 시각은 마지막으로

        clock.set(NOW.plusSeconds(16 * 60));     // 마지막으로 간 뒤 11분
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu)).andExpect(status().isOk());
        assertThat(count("product_visits")).isEqualTo(2);

        mvc.perform(post("/api/products/" + ended + "/visits").cookie(jisu))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40401"));
        assertThat(count("product_visits")).isEqualTo(2);
    }

    @Test
    void 구매하기를_동시에_두_번_눌러도_기록은_한_줄() throws Exception {
        assertThat(twoAtOnce(() -> mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu))
                .andReturn().getResponse().getStatus())).containsExactly(200, 200);
        assertThat(count("product_visits")).isEqualTo(1);
    }

    @Test
    void 간_기록은_그때_가격_그대로_판매가_끝나면_available_false() throws Exception {
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu));
        clock.set(NOW.plusSeconds(60));
        mvc.perform(post("/api/products/" + tofu + "/visits").cookie(jisu));
        mvc.perform(post("/api/products/" + tofu + "/visits").cookie(minji));

        jdbc.update("UPDATE products SET price = 11000, seller_name = '△△마켓' WHERE id = ?", yogurt);
        jdbc.update("UPDATE products SET active = false WHERE id = ?", tofu);

        mvc.perform(get("/api/me/product-visits").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].name").value("두부면 비빔면 2인분"))
                .andExpect(jsonPath("$.data.items[0].available").value(false))
                .andExpect(jsonPath("$.data.items[0].visitedAt").value("2026-11-09T04:01:00Z"))
                .andExpect(jsonPath("$.data.items[1].productId").value(yogurt))
                .andExpect(jsonPath("$.data.items[1].price").value(9800))
                .andExpect(jsonPath("$.data.items[1].sellerName").value("○○몰"))
                .andExpect(jsonPath("$.data.items[1].available").value(true));
    }

    @Test
    void 간_기록은_커서로_넘기고_같은_시각이면_id로_가른다() throws Exception {
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu));
        mvc.perform(post("/api/products/" + granola + "/visits").cookie(jisu));
        mvc.perform(post("/api/products/" + tofu + "/visits").cookie(jisu));   // 셋 다 NOW

        String first = mvc.perform(get("/api/me/product-visits").param("size", "2").cookie(jisu))
                .andExpect(jsonPath("$.data.items[0].productId").value(tofu))
                .andExpect(jsonPath("$.data.items[1].productId").value(granola))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/me/product-visits").param("size", "2")
                        .param("cursor", (String) JsonPath.read(first, "$.data.nextCursor")).cookie(jisu))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productId").value(yogurt))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 기록_모두_지우기는_내_것만() throws Exception {
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu));
        mvc.perform(post("/api/products/" + granola + "/visits").cookie(jisu));
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(minji));

        mvc.perform(delete("/api/me/product-visits").cookie(jisu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
        mvc.perform(delete("/api/me/product-visits").cookie(jisu))
                .andExpect(jsonPath("$.data").value(0));
        assertThat(count("product_visits")).isEqualTo(1);   // 민지 것은 남음
    }

    @Test
    void 기록_모두_지우기는_판매처로_이동과_같은_사람_잠금을_기다린다() throws Exception {
        mvc.perform(post("/api/products/" + yogurt + "/visits").cookie(jisu));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // 다른 트랜잭션이 지수의 「판매처로 이동」 잠금을 쥐고 있는 상태를 만든다
            Future<?> holder = pool.submit(() -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(hashtext('product_visits'),"
                        + " CAST(? % 2147483647 AS integer))", Integer.class, jisuId);
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> clear = pool.submit(() ->
                    mvc.perform(delete("/api/me/product-visits").cookie(jisu)).andReturn().getResponse().getStatus());
            // 지우기 요청이 advisory 잠금을 기다리는 게 DB에 보일 때까지 (시간으로 재지 않음)
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (advisoryWaiters() == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(advisoryWaiters()).isEqualTo(1);
            assertThat(clear.isDone()).isFalse();
            assertThat(count("product_visits")).isEqualTo(1);

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            assertThat(clear.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(count("product_visits")).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private int advisoryWaiters() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                + " AND wait_event_type = 'Lock' AND wait_event = 'advisory'", Integer.class);
    }

    @Test
    void 다른_목록의_커서는_400() throws Exception {
        mvc.perform(put("/api/products/" + yogurt + "/like").cookie(jisu));
        mvc.perform(put("/api/products/" + granola + "/like").cookie(jisu));
        String body = mvc.perform(get("/api/me/liked-products").param("size", "1").cookie(jisu))
                .andReturn().getResponse().getContentAsString();
        String likesCursor = JsonPath.read(body, "$.data.nextCursor");

        mvc.perform(get("/api/me/product-visits").param("cursor", likesCursor).cookie(jisu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SHOP_40001"));
    }
}
