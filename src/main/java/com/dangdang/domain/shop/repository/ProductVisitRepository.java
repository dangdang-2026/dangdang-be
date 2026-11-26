package com.dangdang.domain.shop.repository;

import com.dangdang.domain.shop.entity.ProductVisit;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductVisitRepository extends JpaRepository<ProductVisit, Long> {

    // 같은 사람의 「판매처로 이동」을 한 사람씩 차례로 (트랜잭션 끝나면 풀림)
    //   더블탭으로 두 요청이 거의 동시에 오면 둘 다 「10분 안 기록 없음」을 보고 두 줄을 넣는 걸 막음
    //   「10분 안」 규칙이라 UNIQUE 를 걸 수 없어서 잠금. 사람 줄(users)은 잠그지 않아 다른 기능은 안 기다린다
    @Query(nativeQuery = true, value = """
            SELECT 1 FROM pg_advisory_xact_lock(hashtext('product_visits'), CAST(:userId % 2147483647 AS integer))
            """)
    Integer lockUser(@Param("userId") Long userId);

    // 같은 상품으로 since 뒤에 간 기록 (두 번 눌러도 한 줄로 합칠 때)
    Optional<ProductVisit> findFirstByUserIdAndProductIdAndVisitedAtAfterOrderByVisitedAtDescIdDesc(
            Long userId, Long productId, Instant since);

    // 판매처로 간 기록 (M-22). 최근 것 먼저 (같은 시각이면 id). 내린 상품도 보여 준다 (프론트는 「판매 끝남」)
    @Query("""
            select v from ProductVisit v join fetch v.product
            where v.user.id = :userId
            order by v.visitedAt desc, v.id desc
            """)
    List<ProductVisit> findMine(@Param("userId") Long userId, Limit limit);

    @Query("""
            select v from ProductVisit v join fetch v.product
            where v.user.id = :userId
              and (v.visitedAt < :at or (v.visitedAt = :at and v.id < :id))
            order by v.visitedAt desc, v.id desc
            """)
    List<ProductVisit> findMineBefore(@Param("userId") Long userId, @Param("at") Instant at, @Param("id") long id,
                                      Limit limit);

    // 「기록 모두 지우기」. 지운 줄 수
    @Modifying
    @Query("delete from ProductVisit v where v.user.id = :userId")
    int removeAll(@Param("userId") Long userId);
}
