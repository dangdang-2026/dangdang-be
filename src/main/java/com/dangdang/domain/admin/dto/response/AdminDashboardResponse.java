package com.dangdang.domain.admin.dto.response;

import com.dangdang.domain.admin.enums.DashboardPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 운영자 대시보드 한 번에 (운영 v2 · 대시보드)
public record AdminDashboardResponse(
        @Schema(description = "이 숫자를 센 시각") Instant asOf,
        DashboardPeriod period,
        @Schema(description = "기간 첫날 (한국 날짜)", example = "2026-11-19") LocalDate from,
        @Schema(description = "기간 마지막 날 = 오늘 (한국 날짜)", example = "2026-11-25") LocalDate to,
        Todo todo,
        Summary summary,
        @Schema(description = "날짜별 숫자. 오래된 날부터, 기록이 없는 날도 0으로 채운다") List<Day> daily) {

    // 오늘 처리할 일 — 기간과 상관없이 지금 기준
    public record Todo(
            @Schema(description = "처리 전 신고. 같은 글·댓글에 들어온 신고는 1건으로 센다") long pendingReports,
            @Schema(description = "그중 걱정되는 글(CRISIS)이 있는 글·댓글 수") long crisisReports,
            @Schema(description = "처리 전 신고 중 가장 오래된 신고 시각. 없으면 null") Instant oldestPendingReportAt,
            @Schema(description = "지금부터 오늘 자정(한국) 전까지 풀리는 이용 제한") long sanctionsEndingToday,
            @Schema(description = "아직 걸려 있는 제한에 들어온 이의 신청") long openAppeals,
            @Schema(description = "시작 전인 당당 소식") long scheduledAnnouncements,
            @Schema(description = "가장 먼저 올라갈 예약 소식 시각. 없으면 null") Instant nextAnnouncementAt) {
    }

    // 기간 전체 숫자
    public record Summary(
            @Schema(description = "가입한 산모 (운영자 계정 제외, 지금 기준)") long totalUsers,
            @Schema(description = "기간 안에 혈당이나 식사를 한 번이라도 기록한 산모. 날짜별 숫자를 더한 값과 다르다") long activeUsers,
            @Schema(description = "기간 안에 가입한 산모") long newUsers,
            long readings,
            long meals,
            long posts,
            @Schema(description = "기간 안에 들어온 신고 건수 (자동 신고 포함)") long reports) {
    }

    public record Day(LocalDate date, long activeUsers, long readings, long meals, long posts, long reports) {
    }
}
