package com.dangdang.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 운영자 처리 기록 한 쪽 (운영 v2 · 처리 기록). 기록은 고치거나 지울 수 없다
public record AdminLogPageResponse(List<Item> items, int page, int size, boolean hasNext) {

    public record Item(
            Instant occurredAt,
            @Schema(description = "한 운영자 이름. 계정이 지워졌으면 null") String adminName,
            @Schema(description = "REPORT · SANCTION · ANNOUNCEMENT · CARD") String category,
            @Schema(description = "REPORT_HIDE · REPORT_UNHIDE · REPORT_DISMISS · SANCTION_CREATE · SANCTION_LIFT · "
                    + "ANNOUNCEMENT_CREATE · ANNOUNCEMENT_END · CARD_CREATE · CARD_PUBLISH · CARD_CORRECT · CARD_REVIEW · CARD_WITHDRAW")
            String action,
            @Schema(description = "POST · COMMENT · SANCTION · ANNOUNCEMENT · CARD") String targetType,
            Long targetId,
            @Schema(description = "글 제목·댓글 앞부분·닉네임·소식 제목·카드 제목") String targetLabel,
            @Schema(description = "신고 처리만 — 글쓴이 닉네임") String targetAuthor,
            @Schema(description = "신고·제한 사유, 카드 정정 한 줄") String reason,
            @Schema(description = "운영자 메모, 기간·자리 같은 덧붙임") String memo) {
    }
}
