package com.dangdang.domain.admin.dto.response;

import com.dangdang.domain.admin.enums.AnnouncementState;
import com.dangdang.domain.announcement.enums.AnnouncementKind;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 운영자 당당 소식 목록 (운영 v2 · 당당 소식). 탭 숫자와 자리 현황도 같이 준다
public record AdminAnnouncementListResponse(
        long liveCount,
        long scheduledCount,
        @Schema(description = "끝남 + 예약 취소") long endedCount,
        @Schema(description = "자리마다 지금 올라가 있는 개수와 최대 칸 수. 꽉 찼으면 화면에서 그 자리를 못 고르게 한다") List<Slot> slots,
        List<Item> items) {

    public record Slot(AnnouncementSlot slot, long used, int max, boolean full) {
    }

    public record Item(
            @Schema(description = "내리기·예약 취소(POST /api/admin/announcements/{id}/end)에 쓴다") Long id,
            AnnouncementSlot slot,
            AnnouncementKind kind,
            String title,
            String imageUrl,
            String linkUrl,
            @Schema(description = "카드뉴스를 걸었으면 그 카드 요약, 아니면 null") LinkedCard card,
            Instant activeFrom,
            @Schema(description = "null = 내릴 때까지") Instant activeTo,
            @Schema(description = "광고(AD)를 심사한 사람") String reviewedBy,
            AnnouncementState state) {
    }

    public record LinkedCard(Long id, String title, int slideCount, int sourceCount) {
    }
}
