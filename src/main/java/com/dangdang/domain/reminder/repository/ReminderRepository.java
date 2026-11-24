package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 이 혈당들이 끝낸 알림 (오늘 화면에서 "알림 예정 시각 대비 언제 쟀는지" 계산용)
    List<Reminder> findByReadingIdIn(Collection<Long> readingIds);

    // 기간 안 이 시점들 알림 중 이 상태인 것 (혈당 통계의 "놓친 측정" — 보냈거나 열었는데 혈당이 안 붙은 알림)
    long countByUserIdAndSlotInAndStatusInAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Long userId, Collection<MeasureSlot> slots, Collection<ReminderStatus> statuses, Instant from, Instant to);

    // 공복과 자기 전 알림을 이 시각으로 이미 만들었는지 (30초마다 돌아도 하루 한 번만)
    boolean existsByUserIdAndSlotAndScheduledAt(Long userId, MeasureSlot slot, Instant scheduledAt);

    // 그날(한국) 그 시점 알림 중 아직 안 끝난 것 — 식사 없는 혈당(공복, 자기 전)을 저장하면 끝 처리
    List<Reminder> findByUserIdAndSlotAndMealIdIsNullAndStatusInAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByIdAsc(
            Long userId, MeasureSlot slot, Collection<ReminderStatus> statuses, Instant from, Instant to);

    // 아직 안 보낸 내 알림 — 입원·출산하면 한꺼번에 취소한다
    List<Reminder> findByUserIdAndStatus(Long userId, ReminderStatus status);

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


    // 보내기 직전에 「내가 보낸다」고 잡는다 — 아직 예약 상태이고, 울릴 시각이 됐고, 아무도 안 잡았을 때만 1
    // (다른 서버가 잡고 있거나, 그사이 보냄·미룸·취소로 바뀌었으면 0 → 이 알림은 건너뜀)
    @Modifying
    @Query("""
            UPDATE Reminder r SET r.claimedUntil = :until
            WHERE r.id = :id
              AND r.status = com.dangdang.domain.reminder.enums.ReminderStatus.SCHEDULED
              AND COALESCE(r.snoozedUntil, r.scheduledAt) <= :now
              AND (r.claimedUntil IS NULL OR r.claimedUntil < :now)
            """)
    int claimDue(Long id, Instant now, Instant until);

    // 재알림을 보내기 직전에 잡는다 — 보낸 상태이고, 아직 재알림 전이고, 아무도 안 잡았을 때만 1
    @Modifying
    @Query("""
            UPDATE Reminder r SET r.claimedUntil = :until
            WHERE r.id = :id
              AND r.status = com.dangdang.domain.reminder.enums.ReminderStatus.SENT
              AND r.renotified = false
              AND (r.claimedUntil IS NULL OR r.claimedUntil < :now)
            """)
    int claimRenotify(Long id, Instant now, Instant until);

    // 잡은 것을 푼다 — 푸시가 잠깐 실패해서 다음 바퀴에 다시 시도할 때
    @Modifying
    @Query("UPDATE Reminder r SET r.claimedUntil = NULL WHERE r.id = :id")
    int release(Long id);

}
