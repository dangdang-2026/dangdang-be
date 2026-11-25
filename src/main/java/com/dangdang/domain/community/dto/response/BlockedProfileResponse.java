package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.ProfileBlock;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 마이 › 안 보는 사람 (M-27) 한 줄. 익명 글·댓글에서 막은 줄은 닉네임·프로필 번호를 절대 안 준다 (U-20)
public record BlockedProfileResponse(
        @Schema(description = "이 줄 번호. DELETE /api/blocks/entries/{blockId}로 푼다", example = "15") String blockId,
        @Schema(description = "프로필 번호. 익명 줄이면 null", example = "102") String profileId,
        @Schema(description = "닉네임. 익명 줄이면 「익명 사용자」", example = "포근한 고구마") String nickname,
        @Schema(description = "익명 글·댓글에서 막은 줄") boolean anonymous,
        Instant blockedAt) {

    private static final String ANONYMOUS_NAME = "익명 사용자";

    public static BlockedProfileResponse from(ProfileBlock block) {
        if (block.isViaAnonymous()) {
            return new BlockedProfileResponse(String.valueOf(block.getId()), null, ANONYMOUS_NAME, true,
                    block.getCreatedAt());
        }
        return new BlockedProfileResponse(
                String.valueOf(block.getId()),
                String.valueOf(block.getBlockedProfile().getId()),
                block.getBlockedProfile().getNickname(),
                false,
                block.getCreatedAt());
    }
}
