package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.Visit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 진료 일정 DB 저장·조회
public interface VisitRepository extends JpaRepository<Visit, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<Visit> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 목록 한 페이지: 진료 날짜 이른 순, 날짜 미정(null)은 맨 뒤, 같으면 먼저 만든 순
    // Slice라서 전체 개수(count)는 안 셈 → 다음 페이지가 있는지만 앎
    @Query("""
            select v from Visit v
            where v.user.id = :userId
              and v.deletedAt is null
            order by v.visitAt asc nulls last, v.id asc
            """)
    Slice<Visit> findPage(@Param("userId") Long userId, Pageable pageable);

    // 기간 안 진료 시각만 (기록 달력에서 진료일 표시용). from 이상 to 미만, 날짜 미정은 빠짐
    @Query("""
            select v.visitAt from Visit v
            where v.user.id = :userId
              and v.deletedAt is null
              and v.visitAt >= :from
              and v.visitAt < :to
            """)
    List<Instant> findVisitAtsInRange(@Param("userId") Long userId, @Param("from") Instant from,
                                      @Param("to") Instant to);
}
