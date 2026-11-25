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

    @Column(length = 50)
    private String placeName;                // 어디서 하나요 (E-08) — 고른 병원 이름이나 직접 적은 글자 그대로. 없으면 null

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

    public static PostpartumTest of(User user, String name, LocalDate testDate, String placeName,
                                    boolean remindDayBefore, boolean remindSameDay, Instant now) {
        PostpartumTest t = new PostpartumTest();
        t.user = user;
        t.name = name;
        t.testDate = testDate;
        t.placeName = blankToNull(placeName);
        t.remindDayBefore = remindDayBefore;
        t.remindSameDay = remindSameDay;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    // 보낸 칸만 바꾼다. 날짜를 바꾸면 알림을 다시 보낼 수 있게 「보냄」 표시를 지운다
    // placeName: null = 그대로, 빈 글 = 지우기
    public void change(String name, LocalDate testDate, String placeName, Boolean remindDayBefore,
                       Boolean remindSameDay, Instant now) {
        if (name != null) this.name = name;
        if (testDate != null && !testDate.equals(this.testDate)) {
            this.testDate = testDate;
            this.dayBeforeSentAt = null;
            this.sameDaySentAt = null;
        }
        if (placeName != null) this.placeName = blankToNull(placeName);
        if (remindDayBefore != null) this.remindDayBefore = remindDayBefore;
        if (remindSameDay != null) this.remindSameDay = remindSameDay;
        this.updatedAt = now;
    }

    public void writeResult(String note, Instant now) {
        this.resultNote = note;
        this.updatedAt = now;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
