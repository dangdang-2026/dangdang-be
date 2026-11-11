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
}
