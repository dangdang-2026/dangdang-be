package com.dangdang.domain.insulin.repository;

import com.dangdang.domain.insulin.entity.InsulinLog;
import com.dangdang.domain.insulin.enums.InsulinType;
import com.dangdang.domain.meal.enums.MealSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 인슐린 기록 DB 저장·조회. 기간은 전부 from 이상 to 미만 (한국 날짜 → Instant 변환은 KoreanTime)
public interface InsulinLogRepository extends JpaRepository<InsulinLog, Long> {

    // 하나 찾으면서 이 줄을 잠금: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    // 수정·삭제가 같이 씀. 같은 기록에 요청이 동시에 오면 앞 요청이 끝날 때까지 기다린 뒤 최신 상태(삭제 여부 포함)를 읽음
    // → 늦게 온 수정이 먼저 끝난 삭제를 되살리거나 다른 수정을 덮어쓰지 않음
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InsulinLog l where l.id = :id and l.user.id = :userId and l.deletedAt is null")
    Optional<InsulinLog> findMineForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 기간 안 기록 전부 (오늘 화면·기록 탭·진료 리포트용). 맞은 시각 오래된 순
    @Query("""
            select l from InsulinLog l
            where l.user.id = :userId
              and l.deletedAt is null
              and l.takenAt >= :from
              and l.takenAt < :to
            order by l.takenAt asc, l.id asc
            """)
    List<InsulinLog> findAllInRange(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    // 기간 안 기록 한 페이지: 맞은 시각 오래된 순 (같으면 먼저 적은 순)
    // Slice라서 전체 개수(count)는 안 셈 → 다음 페이지가 있는지만 앎
    @Query("""
            select l from InsulinLog l
            where l.user.id = :userId
              and l.deletedAt is null
              and l.takenAt >= :from
              and l.takenAt < :to
            order by l.takenAt asc, l.id asc
            """)
    Slice<InsulinLog> findPage(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to,
                               Pageable pageable);

    // 지난 기록 불러오기 — 식사 때: 같은 종류 + 같은 끼니 중 맞은 시각이 가장 늦은 1건 (같으면 id 큰 것)
    Optional<InsulinLog> findFirstByUserIdAndInsulinTypeAndMealSlotAndDeletedAtIsNullOrderByTakenAtDescIdDesc(
            Long userId, InsulinType insulinType, MealSlot mealSlot);

    // 지난 기록 불러오기 — 자기 전: 끼니가 없는 같은 종류 중 맞은 시각이 가장 늦은 1건
    Optional<InsulinLog> findFirstByUserIdAndInsulinTypeAndMealSlotIsNullAndDeletedAtIsNullOrderByTakenAtDescIdDesc(
            Long userId, InsulinType insulinType);
}
