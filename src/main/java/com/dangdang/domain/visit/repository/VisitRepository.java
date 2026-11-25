package com.dangdang.domain.visit.repository;

import com.dangdang.domain.visit.entity.Visit;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // 수정용: 이 줄을 잠그고 읽음 (내 것 + 삭제 안 된 것만)
    // 스케줄러가 알림 「처리함」을 적는 중이면 끝날 때까지 기다린 뒤 최신 값(보낸 시각 포함)을 읽음
    // → 날짜를 바꿀 때 보낸 시각을 확실히 비우고, 옛 날짜 알림은 조건부 UPDATE에서 걸러짐
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visit v where v.id = :id and v.user.id = :userId and v.deletedAt is null")
    Optional<Visit> findMineForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 전날 알림 차례: 진료 시각이 from 이상 to 미만(= 내일 하루) · 살아 있음 · 아직 처리 안 함 (회원도 같이 읽음)
    @Query("""
            select v from Visit v join fetch v.user
            where v.deletedAt is null
              and v.visitAt >= :from and v.visitAt < :to
              and v.dayBeforeSentAt is null
            order by v.visitAt asc, v.id asc
            """)
    List<Visit> findDayBeforeDue(@Param("from") Instant from, @Param("to") Instant to);

    // 당일 알림 차례: 진료 시각이 now 이후 to 미만(= 오늘 남은 시간) · 살아 있음 · 아직 처리 안 함
    // 이미 지난 진료(지난 시각으로 적은 일정 포함)는 「오늘 진료가 있어요」를 안 보냄
    @Query("""
            select v from Visit v join fetch v.user
            where v.deletedAt is null
              and v.visitAt > :now and v.visitAt < :to
              and v.sameDaySentAt is null
            order by v.visitAt asc, v.id asc
            """)
    List<Visit> findSameDayDue(@Param("now") Instant now, @Param("to") Instant to);

    // 전날 알림을 「처리함」으로 적음. 읽은 뒤에 날짜가 바뀌었거나 지워졌거나 이미 처리했으면 0 → 보내지 않음
    @Modifying(clearAutomatically = true)
    @Query("""
            update Visit v set v.dayBeforeSentAt = :now
            where v.id = :id and v.visitAt = :visitAt and v.deletedAt is null and v.dayBeforeSentAt is null
            """)
    int claimDayBefore(@Param("id") Long id, @Param("visitAt") Instant visitAt, @Param("now") Instant now);

    // 당일 알림을 「처리함」으로 적음 (조건은 전날과 같음)
    @Modifying(clearAutomatically = true)
    @Query("""
            update Visit v set v.sameDaySentAt = :now
            where v.id = :id and v.visitAt = :visitAt and v.deletedAt is null and v.sameDaySentAt is null
            """)
    int claimSameDay(@Param("id") Long id, @Param("visitAt") Instant visitAt, @Param("now") Instant now);

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
}
