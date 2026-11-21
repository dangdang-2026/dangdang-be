package com.dangdang.domain.visit.dto.request;

import com.dangdang.domain.visit.enums.ReportKind;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// 리포트 저장 요청 (C-07 저장·공유, C-25 「이 기간으로 만들기」)
// 진료용(VISIT)은 visitId만 씀. 기간·병원은 서버가 진료 일정에서 계산하므로 보내도 안 씀
// 기간 직접(CUSTOM)은 from·to(필수)와 careSiteId(선택)를 씀
public record VisitReportCreateRequest(
        @NotNull(message = "kind를 보내 주세요. (VISIT 또는 CUSTOM)")
        ReportKind kind,

        Long visitId,                // VISIT이면 꼭 보냄, CUSTOM이면 안 보냄

        LocalDate from,              // CUSTOM이면 꼭 보냄. 기간 시작 (포함), JSON은 "2026-10-01"

        LocalDate to,                // CUSTOM이면 꼭 보냄. 기간 끝 (포함)

        Long careSiteId) {           // CUSTOM일 때 보여 줄 내 병원 (없어도 됨)
}
