package com.dangdang.domain.meal.repository;

import com.dangdang.domain.meal.entity.Meal;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 식사 기록 DB 저장·조회
public interface MealRepository extends JpaRepository<Meal, Long> {

    // 하나 찾기: 이 id + 내 것 + 삭제 안 된 것만 (아니면 Optional.empty)
    Optional<Meal> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

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
}
