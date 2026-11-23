package com.dangdang.domain.announcement.dto.response;

import com.dangdang.domain.announcement.entity.Announcement;
import com.dangdang.domain.announcement.enums.AnnouncementKind;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import com.dangdang.domain.card.entity.ContentCard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

public record AnnouncementResponse(Long id, AnnouncementSlot slot, AnnouncementKind kind, String title,
                                   String imageUrl, String linkUrl, Long contentCardId,
                                   @Schema(description = "연결된 카드 요약 (카드가 없으면 null)") LinkedCard card,
                                   Instant activeFrom, Instant activeTo) {

    // 커뮤니티 메인 소식 칸에 「출처 n · n장 · 확인일」
    public record LinkedCard(int slideCount, int sourceCount, LocalDate checkedOn) {
        static LinkedCard of(ContentCard c) {
            return c == null ? null : new LinkedCard(c.getBody().size(), c.getSources().size(), c.getCheckedOn());
        }
    }

    public static AnnouncementResponse from(Announcement a, ContentCard card) {
        return new AnnouncementResponse(a.getId(), a.getSlot(), a.getKind(), a.getTitle(), a.getImageUrl(),
                a.getLinkUrl(), a.getContentCardId(), LinkedCard.of(card), a.getActiveFrom(), a.getActiveTo());
    }
}
