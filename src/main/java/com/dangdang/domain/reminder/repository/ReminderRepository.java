package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    //스케줄러용 — 미룬 알림은 미룬 시각 기준으로, 오래된 것부터
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.status = :status AND COALESCE(r.snoozedUntil, r.scheduledAt) <= :now
            ORDER BY COALESCE(r.snoozedUntil, r.scheduledAt)
            """)
    List<Reminder> findDue(ReminderStatus status, Instant now, Limit limit);

    // 10분 뒤 한 번 더 보낼 후보 — 설정을 끈 사람과 직접 미룬 알림은 뺀다
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.status = com.dangdang.domain.reminder.enums.ReminderStatus.SENT
              AND r.renotified = false
              AND r.snoozedUntil IS NULL
              AND r.sentAt <= :sentBefore
              AND r.sentAt > :tooOld
              AND NOT EXISTS (
                  SELECT 1 FROM NotificationPreference p
                  WHERE p.user = r.user AND (p.renotifyEnabled = false OR p.measurementEnabled = false))
            ORDER BY r.sentAt
            """)
    List<Reminder> findRenotifyDue(Instant sentBefore, Instant tooOld, Limit limit);

    // "내 것만" — 남의 번호면 비어서 나온다
    Optional<Reminder> findByIdAndUserId(Long id, Long userId);

    // 목록 — from 이상, to 미만
    Slice<Reminder> findByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Long userId, Instant from, Instant to, Pageable pageable);

    // 다음 알림 하나 — 미룬 알림은 미룬 시각으로 줄을 세운다
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.user.id = :userId AND r.status = :status
            ORDER BY COALESCE(r.snoozedUntil, r.scheduledAt)
            """)
    List<Reminder> findNextByDueTime(Long userId, ReminderStatus status, Limit limit);

}
