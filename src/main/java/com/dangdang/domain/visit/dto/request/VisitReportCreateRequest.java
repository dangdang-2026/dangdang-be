package com.dangdang.domain.visit.dto.request;

import com.dangdang.domain.visit.enums.ReportKind;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// 리포트 저장 요청 (C-07 저장·공유, C-25 「이 기간으로 만들기」)
public record VisitReportCreateRequest(
        @NotNull(message = "kind를 보내 주세요. (VISIT 또는 CUSTOM)")
        ReportKind kind,

        @NotNull(message = "from을 보내 주세요.")
        LocalDate from,              // 기간 시작 (포함). JSON은 "2026-10-01"

        @NotNull(message = "to를 보내 주세요.")
        LocalDate to,                // 기간 끝 (포함)

        Long visitId,                // VISIT이면 꼭 보냄, CUSTOM이면 안 보냄

        Long careSiteId) {           // 기간 직접일 때 보여 줄 내 병원 (없어도 됨). 진료용은 안 쓰고 그 진료 일정의 병원으로 저장
}
