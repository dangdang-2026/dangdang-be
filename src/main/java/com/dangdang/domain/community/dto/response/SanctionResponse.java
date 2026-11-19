package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.UserSanction;

import java.time.Instant;

public record SanctionResponse(Long id, Long userId, String reason, Instant startsAt, Instant endsAt, Instant liftedAt) {

    public static SanctionResponse from(UserSanction s) {
        return new SanctionResponse(s.getId(), s.getUser().getId(), s.getReason(), s.getStartsAt(), s.getEndsAt(),
                s.getLiftedAt());
    }
}
