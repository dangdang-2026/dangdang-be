package com.dangdang.domain.card.dto.response;

import com.dangdang.domain.card.entity.CardSource;
import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.entity.Slide;
import com.dangdang.domain.card.enums.CardStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 카드 상세 (U-26 · 정정 U-26b · 내려감 U-26c). 공감 수는 없다 (COM-12 「공감 수 숨김」)
public record CardDetailResponse(
        Long id, String title, String series, CardStatus status,
        @Schema(description = "슬라이드 (내려간 카드면 빈 목록)") List<Slide> slides,
        @Schema(description = "출처 (내려간 카드면 빈 목록)") List<CardSource> sources,
        LocalDate checkedOn, String reviewer, LocalDate reviewedOn,
        @Schema(description = "정정 문구 (CORRECTED일 때, 내려간 카드면 null)") String correctionNote, Instant correctedAt,
        Instant withdrawnAt, boolean saved) {

    public static CardDetailResponse of(ContentCard c, boolean saved) {
        CardStatus status = CardSummaryResponse.userStatus(c.getStatus());
        boolean gone = status == CardStatus.WITHDRAWN;
        return new CardDetailResponse(c.getId(), c.getTitle(), c.getSeries(), status,
                gone ? List.of() : c.getBody(), gone ? List.of() : c.getSources(),
                c.getCheckedOn(), gone ? null : c.getReviewer(), gone ? null : c.getReviewedOn(),
                gone ? null : c.getCorrectionNote(), gone ? null : c.getCorrectedAt(), c.getWithdrawnAt(), saved);
    }
}
