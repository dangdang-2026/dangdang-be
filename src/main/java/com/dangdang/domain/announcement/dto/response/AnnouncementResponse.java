package com.dangdang.domain.announcement.dto.response;

import com.dangdang.domain.announcement.entity.Announcement;
import com.dangdang.domain.announcement.enums.AnnouncementKind;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;

import java.time.Instant;

public record AnnouncementResponse(Long id, AnnouncementSlot slot, AnnouncementKind kind, String title,
                                   String imageUrl, String linkUrl, Long contentCardId,
                                   Instant activeFrom, Instant activeTo) {

    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getSlot(), a.getKind(), a.getTitle(), a.getImageUrl(),
                a.getLinkUrl(), a.getContentCardId(), a.getActiveFrom(), a.getActiveTo());
    }
}
