package com.dangdang.domain.announcement.entity;

import com.dangdang.domain.announcement.enums.AnnouncementKind;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "announcements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnnouncementSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AnnouncementKind kind;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String linkUrl;

    private Long contentCardId;

    @Column(nullable = false)
    private Instant activeFrom;

    private Instant activeTo;

    @Column(length = 50)
    private String reviewedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Announcement of(AnnouncementSlot slot, AnnouncementKind kind, String title, String imageUrl,
                                  String linkUrl, Long contentCardId, Instant activeFrom, Instant activeTo,
                                  String reviewedBy, Instant now) {
        Announcement a = new Announcement();
        a.slot = slot;
        a.kind = kind;
        a.title = title;
        a.imageUrl = imageUrl;
        a.linkUrl = linkUrl;
        a.contentCardId = contentCardId;
        a.activeFrom = activeFrom;
        a.activeTo = activeTo;
        a.reviewedBy = reviewedBy;
        a.createdAt = now;
        return a;
    }

    // 내리기 = 지금으로 끝 시각을 당긴다 (기록은 남김)
    public void end(Instant now) {
        if (activeTo == null || activeTo.isAfter(now)) {
            activeTo = now;
        }
    }
}
