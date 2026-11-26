package com.dangdang.domain.visit.dto.response;

import com.dangdang.domain.visit.enums.ReportKind;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 지난 리포트 목록 한 페이지 (C-24, 임신 중 기록 보관함 E-10b·E-10c)
public record VisitReportPageResponse(
        List<Item> items,            // 결과가 없으면 []
        int page,                    // 0부터
        int size,
        boolean hasNext,             // C-24가 씀 (그대로 둠)
        long totalCount,             // 조건에 맞는 전체 리포트 수 (보관함 「리포트 6장」)
        int totalPages) {            // 전체 쪽 수 (보관함 쪽 번호 ‹ 1 2 ›). 리포트가 없으면 0

    public static VisitReportPageResponse of(List<Item> items, Page<?> page) {
        return new VisitReportPageResponse(items, page.getNumber(), page.getSize(), page.hasNext(),
                page.getTotalElements(), page.getTotalPages());
    }

    public record Item(
            Long id,                 // 저장한 리포트 id (상세: GET /api/visit-reports/{id})
            ReportKind kind,
            LocalDate from,          // 진료용은 지금 진료 일정으로 계산 (진료 날짜를 미정으로 바꿨으면 null)
            LocalDate to,
            Long visitId,            // 진료용이면 그 일정, 기간 직접이면 null
            Long careSiteId,         // 진료용은 지금 진료 일정의 병원, 기간 직접은 저장한 병원 (없으면 null)
            String careSiteName,     // 병원 이름 (병원이 없거나 지웠으면 null)
            LocalDate visitDate,     // 진료용이면 진료 날짜 (날짜 미정이면 null), 기간 직접이면 null
            Summary summary,         // 카드의 「목표 밖 4번 · 측정 49번」. 조회할 때 다시 계산 (기간이 null이면 null)
            Instant createdAt) {     // 저장한 시각
    }

    public record Summary(
            int measured,            // 측정
            Double inTargetRate,     // 목표 안 비율 0~1 (목표와 비교한 기록이 없으면 null)
            int outOfTarget,         // 목표 밖
            boolean compared) {      // 목표와 비교한 기록이 하나라도 있는지 (false면 「기준 받기 전」)
    }
}
