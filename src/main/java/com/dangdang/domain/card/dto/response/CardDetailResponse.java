package com.dangdang.domain.card.dto.response;

import com.dangdang.domain.card.entity.CardSource;
import com.dangdang.domain.card.entity.ContentCard;
import com.dangdang.domain.card.entity.Slide;
import com.dangdang.domain.card.enums.CardSeries;
import com.dangdang.domain.card.enums.CardStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 카드 상세 (U-26 · 정정 U-26b · 내려감 U-26c). 공감은 내가 눌렀는지만 — 숫자는 없다 (COM-12 「공감 수 숨김」)
public record CardDetailResponse(
        Long id, String title, String series,
        @Schema(description = "시리즈 이름", example = "지침은 이렇게 말해요") String seriesLabel,
        CardStatus status,
        @Schema(description = "슬라이드 (내려간 카드면 빈 목록)") List<Slide> slides,
        @Schema(description = "출처 (내려간 카드면 빈 목록)") List<CardSource> sources,
        @Schema(description = "U-28 물어볼 것에 적기 질문 (내려간 카드면 빈 목록)") List<String> questions,
        LocalDate checkedOn, String reviewer, LocalDate reviewedOn,
        @Schema(description = "정정 요약 한 줄 (CORRECTED일 때, 내려간 카드면 null)") String correctionSummary,
        @Schema(description = "정정 자세한 내용 (없을 수 있음)") String correctionNote, Instant correctedAt,
        Instant withdrawnAt, boolean saved,
        @Schema(description = "내가 공감했는지") boolean reacted) {

    public static CardDetailResponse of(ContentCard c, boolean saved, boolean reacted) {
        CardStatus status = CardSummaryResponse.userStatus(c.getStatus());
        boolean gone = status == CardStatus.WITHDRAWN;
        return new CardDetailResponse(c.getId(), c.getTitle(), c.getSeries(), CardSeries.labelOf(c.getSeries()), status,
                gone ? List.of() : c.getBody(), gone ? List.of() : c.getSources(), gone ? List.of() : c.getQuestions(),
                c.getCheckedOn(), gone ? null : c.getReviewer(), gone ? null : c.getReviewedOn(),
                gone ? null : c.getCorrectionSummary(), gone ? null : c.getCorrectionNote(),
                gone ? null : c.getCorrectedAt(), c.getWithdrawnAt(), saved, reacted);
    }

    // 운영자 응답 — 저장·공감은 의미 없음
    public static CardDetailResponse of(ContentCard c) {
        return of(c, false, false);
    }

    // 운영자 상세 — 상태를 바꾸지 않고(DRAFT·RECHECK 그대로), 내린 카드도 내용까지 보여 준다
    public static CardDetailResponse forAdmin(ContentCard c) {
        return new CardDetailResponse(c.getId(), c.getTitle(), c.getSeries(), CardSeries.labelOf(c.getSeries()),
                c.getStatus(), c.getBody(), c.getSources(), c.getQuestions(), c.getCheckedOn(), c.getReviewer(),
                c.getReviewedOn(), c.getCorrectionSummary(), c.getCorrectionNote(), c.getCorrectedAt(),
                c.getWithdrawnAt(), false, false);
    }
}
