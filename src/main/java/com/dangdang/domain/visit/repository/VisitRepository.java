package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.Visit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 이 시각보다 앞선 진료 중 가장 늦은 진료 시각 (진료용 리포트 기간 시작). 없으면 null
    @Query("""
            select max(v.visitAt) from Visit v
            where v.user.id = :userId
              and v.deletedAt is null
              and v.visitAt < :before
            """)
    Instant findLastVisitAtBefore(@Param("userId") Long userId, @Param("before") Instant before);

    // 내 진료 시각 전부 (날짜 미정·지운 일정 빼고). 지난 리포트 목록에서 진료용 기간을 한 번에 계산하려고
    @Query("""
            select v.visitAt from Visit v
            where v.user.id = :userId
              and v.deletedAt is null
              and v.visitAt is not null
            """)
    List<Instant> findAllVisitAts(@Param("userId") Long userId);

    // C-20 병원 지우기 — 그 병원의 아직 안 온 일정(visit_at >= now)과 날짜 미정 일정을 지운다 (soft delete)
    // 지난 일정은 메모·리포트 때문에 남긴다. 조건을 UPDATE 안에 두어서, 그사이 다른 병원·지난 날짜로 바뀐 일정은 안 지운다
    // 지운 줄 수를 돌려준다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Visit v set v.deletedAt = :now
            where v.user.id = :userId
              and v.careSiteId = :careSiteId
              and v.deletedAt is null
              and (v.visitAt is null or v.visitAt >= :now)
            """)
    int deleteUpcomingAtCareSite(@Param("userId") Long userId, @Param("careSiteId") Long careSiteId,
                                 @Param("now") Instant now);
}
