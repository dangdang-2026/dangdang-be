package com.dangdang.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 운영자 이용 제한 목록 (운영 v2 · 이용 제한). 탭 숫자도 같이 준다
public record AdminSanctionListResponse(
        @Schema(description = "제한 중 탭 숫자") long activeCount,
        @Schema(description = "끝난 제한 탭 숫자") long endedCount,
        @Schema(description = "이의 신청 탭 숫자 — 아직 걸린 제한의 이의") long openAppealCount,
        List<Item> items) {

    public record Item(
            @Schema(description = "제한 번호 — 풀기(POST /api/admin/sanctions/{id}/lift)에 쓴다") Long id,
            Long userId,
            @Schema(description = "커뮤니티 닉네임. 프로필이 없으면 null") String nickname,
            @Schema(description = "앱에서 부르는 이름 — 닉네임이 없을 때 보여 준다") String displayName,
            @Schema(description = "신고 사유와 같은 말", example = "MEDICAL_ADVICE") String reason,
            Instant startsAt,
            @Schema(description = "null = 운영자가 풀 때까지") Instant endsAt,
            Instant liftedAt,
            @Schema(description = "제한을 건 운영자 이름. 운영자 계정이 지워졌으면 null") String createdByName,
            @Schema(description = "이 제한에 이의 신청이 있는지") boolean appealed,
            @Schema(description = "끝난 제한만 — LIFTED(운영자가 풂) · EXPIRED(기간 끝남)") String endedBy,
            @Schema(description = "끝난 제한만 — 끝난 시각") Instant endedAt) {
    }
}
