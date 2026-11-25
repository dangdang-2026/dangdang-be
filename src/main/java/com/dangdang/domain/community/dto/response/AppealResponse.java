package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.SanctionAppeal;

import java.time.Instant;

// 이의 신청 한 건 (사용자 응답 · 운영자 목록 공통)
// sanctionActive: 그 제한이 지금도 걸려 있는지 — false면 이미 풀렸거나 기간이 끝나 처리할 필요 없음 (PR #120 리뷰)
public record AppealResponse(Long id, Long sanctionId, Long userId, String reason, Instant endsAt,
                             boolean sanctionActive, String content, Instant createdAt) {

    public static AppealResponse from(SanctionAppeal a, Instant now) {
        return new AppealResponse(a.getId(), a.getSanction().getId(), a.getUser().getId(), a.getSanction().getReason(),
                a.getSanction().getEndsAt(), a.getSanction().isActiveAt(now), a.getContent(), a.getCreatedAt());
    }
}
