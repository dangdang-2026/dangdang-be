package com.dangdang.domain.reminder.entity;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "reminders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long mealId;

    private Long readingId;                  // 혈당을 저장하면 연결

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeasureSlot slot;

    @Column(nullable = false)
    private Instant scheduledAt;             // 보낼 시각

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReminderStatus status;

    private Instant sentAt;

    private Instant openedAt;

    @Column(nullable = false)
    private boolean renotified = false;

    private Instant snoozedUntil;

    private Instant claimedUntil;            // 보내는 중 — 이 시각까지 다른 서버는 손대지 않는다 (스케줄러만 씀)

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Reminder scheduled(User user, Long mealId, MeasureSlot slot, Instant scheduledAt, Instant now) {
        Reminder r = new Reminder();
        r.user = user;
        r.mealId = mealId;
        r.slot = slot;
        r.scheduledAt = scheduledAt;
        r.status = ReminderStatus.SCHEDULED;
        r.createdAt = now;
        return r;
    }

    // 예약 취소 (식사를 지우거나 시각·끼니를 바꿨을 때). 아직 안 보낸 알림만 부름
    public void cancel() {
        this.status = ReminderStatus.CANCELED;
    }

    // 이 알림의 혈당을 쟀음 → 끝 처리하고 어느 혈당인지 연결함 (이후 스케줄러가 안 보냄)
    public void complete(Long readingId) {
        this.status = ReminderStatus.DONE;
        this.readingId = readingId;
    }

    // 혈당을 고쳐서 이 알림과 안 맞게 됐을 때(다른 식사·다른 날 등) 혈당 연결을 풀고, 혈당이 없었다면의 상태로 돌림
    // 열어 봤으면 OPENED, 보냈으면 SENT (→ 통계의 "놓친 측정"으로 셈), 안 보냈고 울릴 시각이 남았으면 SCHEDULED
    // 안 보냈는데 울릴 시각이 이미 지났으면 SKIPPED — SCHEDULED로 두면 스케줄러가 지난 알림을 뒤늦게 보내 버려서
    public void release(Instant now) {
        this.readingId = null;
        if (this.openedAt != null) {
            this.status = ReminderStatus.OPENED;
        } else if (this.sentAt != null) {
            this.status = ReminderStatus.SENT;
        } else if (dueAt().isAfter(now)) {
            this.status = ReminderStatus.SCHEDULED;
        } else {
            this.status = ReminderStatus.SKIPPED;
        }
    }

    // 보냈다 — 예약 상태일 때만 바뀐다 (보내는 사이 사용자가 먼저 저장했으면 그 상태를 지킨다)
    public void markSent(Instant now) {
        if (this.status == ReminderStatus.SCHEDULED) {
            this.status = ReminderStatus.SENT;
            this.sentAt = now;
        }
    }

    public void markSkipped() {
        if (this.status == ReminderStatus.SCHEDULED) {
            this.status = ReminderStatus.SKIPPED;
        }
    }

    // 처음 연 시각만 남긴다
    public void markOpened(Instant now) {
        this.status = ReminderStatus.OPENED;
        if (this.openedAt == null) {
            this.openedAt = now;
        }
    }

    // 10분 뒤 다시 — 원래 예정 시각(scheduledAt)은 늦게 잼 계산에 쓰니 그대로 둔다
    public void snooze(Instant until) {
        this.status = ReminderStatus.SCHEDULED;
        this.snoozedUntil = until;
        this.claimedUntil = null;            // 미룬 시각에 다시 잡을 수 있게
    }

    public void markRenotified() {
        this.renotified = true;
    }

    // 실제로 울릴 시각 — 미룬 적이 있으면 미룬 시각
    public Instant dueAt() {
        return snoozedUntil != null ? snoozedUntil : scheduledAt;
    }

}
