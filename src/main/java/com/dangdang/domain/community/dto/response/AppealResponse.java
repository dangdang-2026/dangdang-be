package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.SanctionAppeal;

import java.time.Instant;

// 이의 신청 한 건 (사용자 응답 · 운영자 목록 공통)
public record AppealResponse(Long id, Long sanctionId, Long userId, String reason, Instant endsAt, String content,
                             Instant createdAt) {

    public static AppealResponse from(SanctionAppeal a) {
        return new AppealResponse(a.getId(), a.getSanction().getId(), a.getUser().getId(), a.getSanction().getReason(),
                a.getSanction().getEndsAt(), a.getContent(), a.getCreatedAt());
    }
}
