package com.dangdang.domain.shop.repository;

import com.dangdang.domain.shop.entity.ProductLike;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ProductLikeRepository extends JpaRepository<ProductLike, Long> {

    // 찜하기. 이미 있으면 아무 일도 안 한다 — 두 번 눌러도, 동시에 눌러도 한 줄 (uk_product_likes, 시각은 앱 Clock 값)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO product_likes (user_id, product_id, created_at)
            VALUES (:userId, :productId, :now)
            ON CONFLICT (user_id, product_id) DO NOTHING
            """)
    void addIfAbsent(@Param("userId") Long userId, @Param("productId") Long productId, @Param("now") Instant now);

    // 찜 빼기 — 없으면 0줄. 두 번 빠르게 눌러도 둘 다 성공한다
    @Modifying
    @Query("delete from ProductLike l where l.user.id = :userId and l.product.id = :productId")
    void remove(@Param("userId") Long userId, @Param("productId") Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    // 목록 화면에서 하트 칠하기 — 이 상품들 중 내가 찜한 것 (빈 목록이면 부르지 않는다)
    @Query("select l.product.id from ProductLike l where l.user.id = :userId and l.product.id in :productIds")
    Set<Long> findLikedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    // 찜한 상품 수 — 찜한 상품 목록과 같은 조건 (판매 끝난 상품 빼고). M-21 「4개」 · 마이 「저장해 둔 상품 4개」
    @Query("select count(l) from ProductLike l where l.user.id = :userId and l.product.active = true")
    long countMine(@Param("userId") Long userId);

    // 찜한 상품 (M-21). 최근에 찜한 것 먼저 (같은 시각이면 id), 내린 상품은 빼고. 상품은 같이 읽는다
    @Query("""
            select l from ProductLike l join fetch l.product p
            where l.user.id = :userId and p.active = true
            order by l.createdAt desc, l.id desc
            """)
    List<ProductLike> findMine(@Param("userId") Long userId, Limit limit);

    @Query("""
            select l from ProductLike l join fetch l.product p
            where l.user.id = :userId and p.active = true
              and (l.createdAt < :at or (l.createdAt = :at and l.id < :id))
            order by l.createdAt desc, l.id desc
            """)
    List<ProductLike> findMineBefore(@Param("userId") Long userId, @Param("at") Instant at, @Param("id") long id,
                                     Limit limit);
}
