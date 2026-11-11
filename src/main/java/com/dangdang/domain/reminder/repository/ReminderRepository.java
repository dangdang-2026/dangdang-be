package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // 이 식사로 생긴 알림 전부 (상태 상관없이)
    List<Reminder> findByMealId(Long mealId);

    // 이 식사로 생긴 알림 중 특정 상태인 것
    List<Reminder> findByMealIdAndStatus(Long mealId, ReminderStatus status);

    // 이 혈당이 이미 어떤 알림에 붙어 있는지
    boolean existsByReadingId(Long readingId);

    // 이 식사로 생긴 알림 중 상태가 목록 안에 있는 것. 먼저 만든 순
    List<Reminder> findByMealIdAndStatusInOrderByIdAsc(Long mealId, Collection<ReminderStatus> statuses);

    // 스케줄러용 — 시간이 된 예약을 오래된 것부터 한 번에 100개까지
    List<Reminder> findTop100ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(ReminderStatus status, Instant now);

    // "내 것만" — 남의 번호면 비어서 나온다
    Optional<Reminder> findByIdAndUserId(Long id, Long userId);

    // 목록 — from 이상, to 미만
    Slice<Reminder> findByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Long userId, Instant from, Instant to, Pageable pageable);

    // 다음 알림 하나
    Optional<Reminder> findFirstByUserIdAndStatusOrderByScheduledAtAsc(Long userId, ReminderStatus status);

}
