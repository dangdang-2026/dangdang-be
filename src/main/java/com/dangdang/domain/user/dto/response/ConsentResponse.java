package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.user.entity.ConsentEvent;
import com.dangdang.domain.user.enums.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record ConsentResponse(
        @Schema(description = "필수 4개에 모두 지금 문서 버전으로 동의했는지 (false면 A-01)", example = "true") boolean completed,
        List<Item> items) {

    public record Item(
            @Schema(example = "TERMS") ConsentType type,
            @Schema(description = "필수 항목인지", example = "true") boolean required,
            @Schema(description = "지금 동의 상태 — 지금 문서 버전으로 동의했을 때만 true (한 번도 안 고르거나 예전 버전이면 false)", example = "true") boolean granted,
            @Schema(description = "예전 문서 버전에 동의해서 다시 물어야 하는지 — true면 「문서가 바뀌었어요」로 다시 받는다 (granted=false인데 거절과 구분). 거절·안 고름·지금 버전 동의는 false", example = "false") boolean needsReconsent,
            @Schema(description = "마지막으로 고른 문서 버전 (안 고르면 null)", example = "v1") String version,
            @Schema(description = "마지막으로 고른 시각 (안 고르면 null)") Instant occurredAt) {

        public static Item of(ConsentType type, ConsentEvent latestOrNull) {
            if (latestOrNull == null) {
                return new Item(type, type.isRequired(), false, false, null, null);
            }
            return new Item(
                    type,
                    type.isRequired(),
                    latestOrNull.isCurrentGrant(),
                    latestOrNull.isGranted() && !latestOrNull.isCurrentGrant(),
                    latestOrNull.getDocumentVersion(),
                    latestOrNull.getOccurredAt());
        }
    }
}
