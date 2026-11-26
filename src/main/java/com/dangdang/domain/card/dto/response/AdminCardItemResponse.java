package com.dangdang.domain.card.dto.response;

import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.enums.CardSeries;
import com.dangdang.domain.card.enums.CardStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

// 운영자 카드 목록 한 줄 (운영 v2 · 카드뉴스). 사용자 목록 칸에 검수, 정정, 내린 시각을 더했다
public record AdminCardItemResponse(
        Long id, String title, String series,
        @Schema(description = "시리즈 이름", example = "지침은 이렇게 말해요") String seriesLabel,
        @Schema(description = "DRAFT · PUBLISHED · CORRECTED · RECHECK · WITHDRAWN 그대로") CardStatus status,
        LocalDate checkedOn,
        int sourceCount,
        int slideCount,
        Instant createdAt,
        @Schema(description = "검수한 사람. 아직 안 했으면 null", example = "다현") String reviewer,
        @Schema(description = "검수한 날", example = "2026-11-15") LocalDate reviewedOn,
        @Schema(description = "정정한 시각 (CORRECTED일 때)") Instant correctedAt,
        @Schema(description = "내린 시각 (WITHDRAWN일 때)") Instant withdrawnAt) {

    public static AdminCardItemResponse of(ContentCard c) {
        return new AdminCardItemResponse(c.getId(), c.getTitle(), c.getSeries(), CardSeries.labelOf(c.getSeries()),
                c.getStatus(), c.getCheckedOn(), c.getSources().size(), c.getBody().size(), c.getCreatedAt(),
                c.getReviewer(), c.getReviewedOn(), c.getCorrectedAt(), c.getWithdrawnAt());
    }
}
