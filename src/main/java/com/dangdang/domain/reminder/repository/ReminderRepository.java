package com.dangdang.domain.reminder.repository;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // 이 혈당이 끝낸 알림 (reading_id가 unique라 많아야 하나)
    Optional<Reminder> findByReadingId(Long readingId);

    // 이 혈당들이 끝낸 알림 (오늘 화면에서 "알림 예정 시각 대비 언제 쟀는지" 계산용)
    List<Reminder> findByReadingIdIn(Collection<Long> readingIds);

    // 기간 안 이 시점들 알림 중 이 상태인 것 (혈당 통계·진료 리포트의 "놓친 측정" — 보냈거나 열었거나 건너뛰었는데 혈당이 안 붙은 알림)
    // 지운 식사의 알림은 안 셈 (기간 지우기·하나 지우기 뒤 「놓친 측정」만 남지 않게). 알림 상태는 그대로 둬서 되돌리면 다시 셈
    // 식사 없는 알림(공복·자기 전)은 그대로 셈
    @Query("""
            SELECT count(r) FROM Reminder r
            WHERE r.user.id = :userId
              AND r.slot IN :slots
              AND r.status IN :statuses
              AND r.scheduledAt >= :from
              AND r.scheduledAt < :to
              AND (r.mealId IS NULL
                   OR EXISTS (SELECT 1 FROM Meal m WHERE m.id = r.mealId AND m.deletedAt IS NULL))
            """)
    long countMissed(Long userId, Collection<MeasureSlot> slots, Collection<ReminderStatus> statuses,
                     Instant from, Instant to);

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

    // 10분 뒤 한 번 더 보낼 후보 — 설정을 끈 사람과 직접 미룬 알림, 지운 식사의 알림은 뺀다
    // (식사를 지우면 안 보낸 알림만 취소돼서, 이미 보낸 알림은 SENT로 남아 지운 식사에 「잴 시간이에요」가 다시 가므로)
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.status = com.dangdang.domain.reminder.enums.ReminderStatus.SENT
              AND r.renotified = false
              AND r.snoozedUntil IS NULL
              AND r.sentAt <= :sentBefore
              AND r.sentAt > :tooOld
              AND (r.mealId IS NULL
                   OR EXISTS (SELECT 1 FROM Meal m WHERE m.id = r.mealId AND m.deletedAt IS NULL))
              AND NOT EXISTS (
                  SELECT 1 FROM NotificationPreference p
                  WHERE p.user = r.user AND (p.renotifyEnabled = false OR p.measurementEnabled = false))
            ORDER BY r.sentAt
            """)
    List<Reminder> findRenotifyDue(Instant sentBefore, Instant tooOld, Limit limit);

    // "내 것만" — 남의 번호면 비어서 나온다
    Optional<Reminder> findByIdAndUserId(Long id, Long userId);

    // 줄을 잠가서 읽기 — 읽은 상태를 보고 고치는 곳(열기·미루기·보냄·재알림 기록)이 건너뛰기(DISMISSED) 같은
    // 다른 요청의 상태 변경을 옛 값으로 덮지 않게. 건너뛰기의 UPDATE가 먼저면 끝난 뒤의 상태를 읽고,
    // 이쪽이 먼저면 건너뛰기 UPDATE가 기다렸다가 바뀐 상태를 보고 다시 판단함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reminder r where r.id = :id and r.user.id = :userId")
    Optional<Reminder> findMineForUpdate(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reminder r where r.id = :id")
    Optional<Reminder> findByIdForUpdate(Long id);

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
    // 후보로 고른 뒤 식사를 지웠으면 여기서 0 (findRenotifyDue와 같은 살아 있는 식사 조건)
    @Modifying
    @Query("""
            UPDATE Reminder r SET r.claimedUntil = :until
            WHERE r.id = :id
              AND r.status = com.dangdang.domain.reminder.enums.ReminderStatus.SENT
              AND r.renotified = false
              AND (r.mealId IS NULL
                   OR EXISTS (SELECT 1 FROM Meal m WHERE m.id = r.mealId AND m.deletedAt IS NULL))
              AND (r.claimedUntil IS NULL OR r.claimedUntil < :now)
            """)
    int claimRenotify(Long id, Instant now, Instant until);

    // 잡은 것을 푼다 — 푸시가 잠깐 실패해서 다음 바퀴에 다시 시도할 때
    @Modifying
    @Query("UPDATE Reminder r SET r.claimedUntil = NULL WHERE r.id = :id")
    int release(Long id);

    // H-05 이번 끼니 건너뛰기 — 이 식사의 아직 안 끝난 알림(예약·보냄·열어 봄)을 DISMISSED로
    // 상태를 읽고 고치는 쪽(열기·미루기·보냄·재알림 기록)은 줄을 잠가서 읽으니(findMineForUpdate·findByIdForUpdate)
    // 이 UPDATE와 겹쳐도 앞 요청이 끝난 뒤의 상태를 보고, DISMISSED를 옛 값(SENT·OPENED·SCHEDULED)으로 덮지 않음
    // clearAutomatically를 안 붙인다 — 같은 트랜잭션에서 고친 Meal이 영속성 컨텍스트에서 떨어져 저장이 안 될 수 있어서
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Reminder r SET r.status = com.dangdang.domain.reminder.enums.ReminderStatus.DISMISSED
            WHERE r.mealId = :mealId
              AND r.status IN (com.dangdang.domain.reminder.enums.ReminderStatus.SCHEDULED,
                               com.dangdang.domain.reminder.enums.ReminderStatus.SENT,
                               com.dangdang.domain.reminder.enums.ReminderStatus.OPENED)
            """)
    int dismissForMeal(Long mealId);

    // 기간 지우기로 지운 식사들의 아직 안 보낸 식후 알림을 한꺼번에 취소 (cancelForMeal의 여러 개 버전)
    // 보내는 중이면 claimDue가 SCHEDULED 조건으로 잡으니, 여기서 CANCELED가 먼저 되면 안 보냄
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reminder r SET r.status = com.dangdang.domain.reminder.enums.ReminderStatus.CANCELED
            WHERE r.mealId IN :mealIds
              AND r.status = com.dangdang.domain.reminder.enums.ReminderStatus.SCHEDULED
            """)
    int cancelScheduledForMeals(Collection<Long> mealIds);
}
