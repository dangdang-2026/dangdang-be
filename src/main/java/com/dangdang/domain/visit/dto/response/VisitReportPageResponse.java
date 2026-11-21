package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.enums.ReportKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 지난 리포트 목록 한 페이지 (C-24). 전체 개수는 안 셈, 다음 페이지가 있는지만
public record VisitReportPageResponse(
        List<Item> items,            // 결과가 없으면 []
        int page,                    // 0부터
        int size,
        boolean hasNext) {

    public record Item(
            Long id,                 // 저장한 리포트 id (상세: GET /api/visit-reports/{id})
            ReportKind kind,
            LocalDate from,
            LocalDate to,
            Long visitId,            // 진료용이면 그 일정, 기간 직접이면 null
            Long careSiteId,         // 저장한 병원 (없으면 null)
            String careSiteName,     // 병원 이름 (병원이 없거나 지웠으면 null)
            LocalDate visitDate,     // 진료용이면 진료 날짜 (날짜 미정이면 null), 기간 직접이면 null
            Summary summary,         // 카드의 「내 기준 안 82% · 측정 52번」. 조회할 때 다시 계산
            Instant createdAt) {     // 저장한 시각
    }

    public record Summary(
            int measured,            // 측정
            Double inTargetRate,     // 목표 안 비율 0~1 (목표와 비교한 기록이 없으면 null)
            int outOfTarget,         // 목표 밖
            boolean compared) {      // 목표와 비교한 기록이 하나라도 있는지 (false면 「기준 받기 전」)
    }
}
