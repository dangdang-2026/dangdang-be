package com.dangdang.domain.postpartum.entity;

import com.dangdang.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "postpartum_tests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostpartumTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private LocalDate testDate;

    @Column(nullable = false)
    private boolean remindDayBefore;

    @Column(nullable = false)
    private boolean remindSameDay;

    @Column(length = 500)
    private String resultNote;

    private Instant dayBeforeSentAt;
    private Instant sameDaySentAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static PostpartumTest of(User user, String name, LocalDate testDate, boolean remindDayBefore,
                                    boolean remindSameDay, Instant now) {
        PostpartumTest t = new PostpartumTest();
        t.user = user;
        t.name = name;
        t.testDate = testDate;
        t.remindDayBefore = remindDayBefore;
        t.remindSameDay = remindSameDay;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    // 보낸 칸만 바꾼다. 날짜를 바꾸면 알림을 다시 보낼 수 있게 「보냄」 표시를 지운다
    public void change(String name, LocalDate testDate, Boolean remindDayBefore, Boolean remindSameDay, Instant now) {
        if (name != null) this.name = name;
        if (testDate != null && !testDate.equals(this.testDate)) {
            this.testDate = testDate;
            this.dayBeforeSentAt = null;
            this.sameDaySentAt = null;
        }
        if (remindDayBefore != null) this.remindDayBefore = remindDayBefore;
        if (remindSameDay != null) this.remindSameDay = remindSameDay;
        this.updatedAt = now;
    }

    public void writeResult(String note, Instant now) {
        this.resultNote = note;
        this.updatedAt = now;
    }

    public void markDayBeforeHandled(Instant now) {
        this.dayBeforeSentAt = now;
    }

    public void markSameDayHandled(Instant now) {
        this.sameDaySentAt = now;
    }
}
