package com.dangdang.domain.pregnancy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record HospitalizationRequest(
        @Schema(description = "입원했으면 true, 퇴원(알림 다시 켜기)이면 false", example = "true")
        @NotNull(message = "입원했는지 골라 주세요.")
        Boolean hospitalized) {
}
