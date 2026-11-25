package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.CareMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

// 세 PATCH가 모두 같은 모양으로 돌려준다 → 프론트는 받은 값으로 칸을 한 번에 갈아 끼우면 된다
public record PregnancyProfileResponse(
        @Schema(description = "임신성 당뇨 진단일 (안 적었으면 null)", example = "2026-09-24") LocalDate diagnosisDate,
        @Schema(description = "관리 모드 DIET · INSULIN · ORAL", example = "INSULIN") CareMode careMode,
        @Schema(description = "연락 기준 메모 (없으면 null)", example = "같은 시점에 계속 높게 나오면 외래로 전화하기")
        String contactMemo,
        @Schema(description = "연락 기준 메모를 마지막으로 적은 시각 (메모가 없으면 null). G-05 「10월 1일에 적은 메모」",
                example = "2026-10-01T03:12:00Z")
        Instant contactMemoUpdatedAt) {

    public static PregnancyProfileResponse from(Pregnancy p) {
        return new PregnancyProfileResponse(
                p.getDiagnosisDate(), p.getCareMode(), p.getContactMemo(), p.getContactMemoUpdatedAt());
    }
}
