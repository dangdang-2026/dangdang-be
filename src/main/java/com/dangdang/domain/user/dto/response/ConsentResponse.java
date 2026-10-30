package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.user.entity.ConsentEvent;
import com.dangdang.domain.user.enums.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record ConsentResponse(
        @Schema(description = "필수 4개에 모두 동의했는지", example = "true") boolean completed,
                              List<Item> items) {

    public record Item(
            @Schema(example = "TERMS") ConsentType type,
            @Schema(description = "필수 항목인지", example = "true") boolean required,
            @Schema(description = "지금 동의 상태 (한 번도 안 고르면 false)", example = "true") boolean granted,
            @Schema(description = "동의한 문서 버전 (안 고르면 null)", example = "v1") String version,
            @Schema(description = "마지막으로 고른 시각 (안 고르면 null)") Instant occurredAt) {

        public static Item of(ConsentType type, ConsentEvent latestOrNull) {
            if (latestOrNull == null) {
                return new Item(type, type.isRequired(), false, null, null);
            }
            return new Item(
                    type,
                    type.isRequired(),
                    latestOrNull.isGranted(),
                    latestOrNull.getDocumentVersion(),
                    latestOrNull.getOccurredAt());
        }
    }
    }