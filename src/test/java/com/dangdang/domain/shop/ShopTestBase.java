package com.dangdang.domain.shop;

import com.dangdang.support.ReminderTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// 상점 테스트 공통: 지수·민지 로그인 + 상품 4개 (운영자가 SQL로 넣는다고 보고 jdbc로 넣음)
abstract class ShopTestBase extends ReminderTestBase {

    Cookie jisu;
    Cookie minji;
    long yogurt;    // 간식 · PICK · 순서 1
    long granola;   // 간식 · 순서 2
    long tofu;      // 밥·면 대신 · 순서 3
    long ended;     // 판매 끝남

    @BeforeEach
    void setUpShop() throws Exception {
        jdbc.update("DELETE FROM products");   // 마이그레이션이 넣은 예시 상품 7개를 비우고 테스트 상품만으로 시작
        jisu = login("jisu@x.com");
        minji = login("minji@x.com");
        yogurt = product("SNACK", "브랜드 B", "무가당 그릭요거트, 100g, 4개입", 9800, true, 1, true);
        granola = product("SNACK", "브랜드 A", "저당 그래놀라 300g", 12900, false, 2, true);
        tofu = product("MEAL_SWAP", "브랜드 C", "두부면 비빔면 2인분", 6900, false, 3, true);
        ended = product("SNACK", "브랜드 D", "끝난 과자", 3000, false, 0, false);
    }

    @AfterEach
    void cleanProducts() {   // users 는 부모 클래스가 지움 (찜·기록·최근 본 상품은 products 를 지워도 같이 지워짐)
        jdbc.update("DELETE FROM products");
    }

    long product(String category, String brand, String name, int price, boolean pick, int order, boolean active) {
        Long id = jdbc.queryForObject("""
                INSERT INTO products (category, brand, name, size_label, price, image_urls, seller_name, seller_url,
                                      affiliate, pick, sort_order, active)
                VALUES (?, ?, ?, '1개', ?, ?::jsonb, '○○몰', 'https://shop.example.com/tmp', TRUE, ?, ?, ?)
                RETURNING id
                """, Long.class, category, brand, name, price,
                "[\"https://img.example.com/" + name + "-1.png\", \"https://img.example.com/" + name + "-2.png\"]",
                pick, order, active);
        jdbc.update("UPDATE products SET seller_url = ? WHERE id = ?", "https://shop.example.com/goods/" + id, id);
        return id;
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    // 같은 요청 두 개를 한꺼번에 출발시키고 둘의 HTTP 상태를 돌려준다
    List<Integer> twoAtOnce(Callable<Integer> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdown();
        }
    }
}
