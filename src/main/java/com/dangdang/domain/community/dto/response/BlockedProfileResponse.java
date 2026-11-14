package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.ProfileBlock;

import java.time.Instant;

public record BlockedProfileResponse(String profileId, String nickname, Instant blockedAt) {

    public static BlockedProfileResponse from(ProfileBlock block) {
        return new BlockedProfileResponse(
                String.valueOf(block.getBlockedProfile().getId()),
                block.getBlockedProfile().getNickname(),
                block.getCreatedAt());
    }
}
