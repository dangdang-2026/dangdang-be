package com.dangdang.domain.card.dto.response;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardSeries;
import com.dangdang.domain.card.enums.CardStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

// 카드 목록·저장 목록 한 줄 (U-01 공식 카드 줄 · U-27)
public record CardSummaryResponse(Long id, String title, String series,
                                  @Schema(description = "시리즈 이름", example = "지침은 이렇게 말해요") String seriesLabel,
                                  CardStatus status, LocalDate checkedOn,
                                  @Schema(description = "출처 개수 (내려간 카드면 0)") int sourceCount,
                                  @Schema(description = "슬라이드 장 수 (내려간 카드면 0)") int slideCount,
                                  Instant createdAt, boolean saved) {

    // 사용자 목록 — 내려간 카드는 내용을 안 보여 주니 수도 0
    public static CardSummaryResponse of(ContentCard c, boolean saved) {
        CardStatus status = userStatus(c.getStatus());
        return of(c, status, saved, status == CardStatus.WITHDRAWN);
    }


    private static CardSummaryResponse of(ContentCard c, CardStatus status, boolean saved, boolean gone) {
        return new CardSummaryResponse(c.getId(), c.getTitle(), c.getSeries(), CardSeries.labelOf(c.getSeries()),
                status, c.getCheckedOn(), gone ? 0 : c.getSources().size(), gone ? 0 : c.getBody().size(),
                c.getCreatedAt(), saved);
    }

    // RECHECK은 운영자 속사정 → 사용자에겐 PUBLISHED로
    static CardStatus userStatus(CardStatus s) {
        return s == CardStatus.RECHECK ? CardStatus.PUBLISHED : s;
    }
}
