package com.dangdang.domain.pregnancy.dto.response;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record PregnancyStateResponse(
        @Schema(example = "POSTPARTUM") PregnancyState state,
        @Schema(description = "출산일 (산후일 때)", example = "2026-11-08") LocalDate deliveryDate,
        @Schema(description = "이번에 멈춘(취소한) 예정 측정 알림 수", example = "2") int pausedReminders) {

    public static PregnancyStateResponse of(Pregnancy p, int pausedReminders) {
        return new PregnancyStateResponse(p.getState(), p.getDeliveryDate(), pausedReminders);
    }
}
