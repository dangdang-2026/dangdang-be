package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.Meal;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // 식사의 수정 시각만 지금으로 바꿈. 식사 항목이 바뀌면 식사도 바뀐 것으로 봄 (식사 칸 자체는 그대로라 자동으로 안 바뀜)
    @Modifying(flushAutomatically = true)
    @Query("update Meal m set m.updatedAt = :now where m.id = :mealId")
    void touch(Long mealId, Instant now);
}
