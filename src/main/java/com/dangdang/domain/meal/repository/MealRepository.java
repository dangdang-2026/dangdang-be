package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.Meal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 식사 기록 DB 저장·조회
public interface MealRepository extends JpaRepository<Meal, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<Meal> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    // 하나 찾으면서 이 식사 줄을 잠금 (트랜잭션이 끝날 때까지 다른 요청이 이 식사를 못 바꿈)
    // 영양값 직접 입력에서 "수정 시각 확인 → 쓰기"를 한 번에 처리하려고 씀. 다른 항목 수정도 touch에서 이 줄을 바꾸므로 순서대로 기다림
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meal m where m.id = :id and m.user.id = :userId and m.deletedAt is null")
    Optional<Meal> findMineForUpdate(Long id, Long userId);

    // 여러 개 찾기: 이 id들 중 내 것 + 삭제 안 된 것만 (지운 식사는 빠짐)
    List<Meal> findAllByIdInAndUserIdAndDeletedAtIsNull(Collection<Long> ids, Long userId);

    // 목록 한 페이지: 내 것 + 삭제 안 됨 + from 이후 + 커서(cursorAt, cursorId)보다 과거. 시작 시각 최신순
    // 첫 페이지는 cursorAt = 기간 끝(to 다음 날 0시), cursorId = 0 으로 부름 (혈당 목록과 같은 방식)
    @Query("""
            select m from Meal m
            where m.user.id = :userId
              and m.deletedAt is null
              and m.startedAt >= :from
              and (m.startedAt < :cursorAt
                   or (m.startedAt = :cursorAt and m.id < :cursorId))
            order by m.startedAt desc, m.id desc
            """)
    List<Meal> findPage(Long userId, Instant from, Instant cursorAt, Long cursorId, Limit limit);

    // 기간 안 식사 전부 (오늘 화면·기록 통합 조회용). from 이상 to 미만, 시작 시각 오래된 순
    @Query("""
            select m from Meal m
            where m.user.id = :userId
              and m.deletedAt is null
              and m.startedAt >= :from
              and m.startedAt < :to
            order by m.startedAt asc, m.id asc
            """)
    List<Meal> findAllInRange(Long userId, Instant from, Instant to);

    // 기간 안 시작 시각만 (달력에서 기록 있는 날 찾기용)
    @Query("""
            select m.startedAt from Meal m
            where m.user.id = :userId
              and m.deletedAt is null
              and m.startedAt >= :from
              and m.startedAt < :to
            """)
    List<Instant> findStartedAtsInRange(Long userId, Instant from, Instant to);

    // 가장 처음 시작한 식사 시각 (식사가 하나도 없으면 null)
    @Query("""
            select min(m.startedAt) from Meal m
            where m.user.id = :userId
              and m.deletedAt is null
            """)
    Instant findFirstStartedAt(Long userId);

    // 이 음식(foodCode)을 먹은 내 식사 한 페이지 (같은 음식 지난 기록용). 시작 시각 최신순
    // 지운 식사·지운 항목은 빠짐. 한 식사에 같은 음식 항목이 여러 개여도 식사는 한 번만 나옴 (exists라서)
    @Query("""
            select m from Meal m
            where m.user.id = :userId
              and m.deletedAt is null
              and exists (select 1 from MealItem i
                          where i.meal = m
                            and i.foodCode = :foodCode
                            and i.deletedAt is null)
            order by m.startedAt desc, m.id desc
            """)
    Page<Meal> findPageEatenFood(Long userId, String foodCode, Pageable pageable);

    // 식사의 수정 시각만 지금으로 바꿈. 식사 항목이 바뀌면 식사도 바뀐 것으로 봄 (식사 칸 자체는 그대로라 자동으로 안 바뀜)
    @Modifying(flushAutomatically = true)
    @Query("update Meal m set m.updatedAt = :now where m.id = :mealId")
    void touch(Long mealId, Instant now);
}
