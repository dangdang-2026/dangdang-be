package com.dangdang.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 원문 보기 — 가려진 글도 운영자에게는 그대로 보인다
public record AdminReportDetailResponse(
        String targetType,
        String targetId,
        String postId,
        @Schema(description = "글 제목 (댓글이면 null)") String title,
        String content,
        @Schema(description = "글쓴이 회원 번호 — 이용 제한(47번 POST /api/admin/sanctions)에 그대로 넣는다. 탈퇴했으면 null")
        Long authorUserId,
        String authorNickname,
        boolean anonymous,
        Instant createdAt,
        @Schema(description = "글쓴이가 마지막으로 고친 시각. 신고 시각보다 뒤면 신고 뒤에 고친 것 (지금 본문은 고친 뒤 내용)")
        Instant updatedAt,
        @Schema(description = "VISIBLE · HIDDEN · DELETED") String state,
        String hiddenReason,
        List<ReportLine> reports,
        List<ActionLine> actions) {

    // 신고한 사람은 운영자에게도 보여 주지 않는다 (글쓴이에게 알려지지 않게 — 31번)
    public record ReportLine(String reason, String detail, String status,
                             @Schema(description = "USER(사람 신고) · SYSTEM(올리기 전 안내를 보고도 올린 글, 자동)") String source,
                             Instant createdAt) {
    }

    public record ActionLine(String action, String reason, String memo, Long adminId,
                             @Schema(description = "처리한 운영자 이름. 운영자 계정이 지워졌으면 null", example = "다현") String adminName,
                             Instant occurredAt) {
    }
}
