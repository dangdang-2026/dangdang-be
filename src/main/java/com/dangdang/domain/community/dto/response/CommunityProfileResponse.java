package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.CommunityProfile;

public record CommunityProfileResponse(String id, String nickname, boolean showWeekBadge) {

    public static CommunityProfileResponse from(CommunityProfile profile) {
        return new CommunityProfileResponse(
                String.valueOf(profile.getId()), profile.getNickname(), profile.isShowWeekBadge());
    }
}
