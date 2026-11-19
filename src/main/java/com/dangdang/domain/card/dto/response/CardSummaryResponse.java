package com.dangdang.domain.card.dto.response;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardStatus;

import java.time.Instant;
import java.time.LocalDate;

// 카드 목록·저장 목록 한 줄 (U-01 공식 카드 줄 · U-27)
public record CardSummaryResponse(Long id, String title, String series, CardStatus status, LocalDate checkedOn,
                                  Instant createdAt, boolean saved) {

    public static CardSummaryResponse of(ContentCard c, boolean saved) {
        return new CardSummaryResponse(c.getId(), c.getTitle(), c.getSeries(), userStatus(c.getStatus()),
                c.getCheckedOn(), c.getCreatedAt(), saved);
    }

    // 운영자 목록 — 상태를 바꾸지 않고 그대로 (DRAFT·RECHECK도 보인다)
    public static CardSummaryResponse forAdmin(ContentCard c) {
        return new CardSummaryResponse(c.getId(), c.getTitle(), c.getSeries(), c.getStatus(),
                c.getCheckedOn(), c.getCreatedAt(), false);
    }

    // RECHECK은 운영자 속사정 → 사용자에겐 PUBLISHED로
    static CardStatus userStatus(CardStatus s) {
        return s == CardStatus.RECHECK ? CardStatus.PUBLISHED : s;
    }
}
