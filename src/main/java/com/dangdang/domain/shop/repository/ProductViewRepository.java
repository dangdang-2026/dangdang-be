package com.dangdang.domain.shop.repository;

import com.dangdang.domain.shop.entity.ProductView;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductViewRepository extends JpaRepository<ProductView, Long> {

    // 상세를 열 때마다. 처음이면 한 줄 넣고, 본 적 있으면 시각만 새로 (uk_product_views — 동시에 열어도 한 줄)
    // 늦게 도착한 옛 요청이 더 최근 시각을 덮지 않게 둘 중 늦은 시각으로
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO product_views (user_id, product_id, viewed_at)
            VALUES (:userId, :productId, :now)
            ON CONFLICT (user_id, product_id) DO UPDATE SET viewed_at = GREATEST(product_views.viewed_at, EXCLUDED.viewed_at)
            """)
    void upsert(@Param("userId") Long userId, @Param("productId") Long productId, @Param("now") Instant now);

    // 최근 본 상품. since 뒤에 본 것만, 판매 중인 것만, 최근 것 먼저 (같은 시각이면 id)
    @Query("""
            select v from ProductView v join fetch v.product p
            where v.user.id = :userId and p.active = true and v.viewedAt >= :since
            order by v.viewedAt desc, v.id desc
            """)
    List<ProductView> findRecent(@Param("userId") Long userId, @Param("since") Instant since, Limit limit);

    @Query("""
            select v from ProductView v join fetch v.product p
            where v.user.id = :userId and p.active = true and v.viewedAt >= :since
              and (v.viewedAt < :at or (v.viewedAt = :at and v.id < :id))
            order by v.viewedAt desc, v.id desc
            """)
    List<ProductView> findRecentBefore(@Param("userId") Long userId, @Param("since") Instant since,
                                       @Param("at") Instant at, @Param("id") long id, Limit limit);

    // 최근 본 상품 수 — 목록과 같은 조건 (since 뒤 · 판매 중)
    @Query("""
            select count(v) from ProductView v
            where v.user.id = :userId and v.product.active = true and v.viewedAt >= :since
            """)
    long countRecent(@Param("userId") Long userId, @Param("since") Instant since);

    @Modifying
    @Query("delete from ProductView v where v.user.id = :userId and v.product.id = :productId")
    void remove(@Param("userId") Long userId, @Param("productId") Long productId);

    @Modifying
    @Query("delete from ProductView v where v.user.id = :userId")
    void removeAll(@Param("userId") Long userId);
}
