package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.CommunityProfile;

// 글·댓글에 붙는 글쓴이. 이메일·실명은 절대 담지 않는다
public record AuthorResponse(String profileId, String displayName, String weekBadge, boolean isMine) {

    private static final String ANONYMOUS_NAME = "익명 산모";
    private static final String WITHDRAWN_NAME = "탈퇴한 회원";

    public static AuthorResponse of(CommunityProfile profile, boolean anonymous, Long myProfileId) {
        boolean mine = profile != null && profile.getId().equals(myProfileId);
        if (profile == null) {
            return new AuthorResponse(null, WITHDRAWN_NAME, null, false);
        }
        if (anonymous) {
            return new AuthorResponse(null, ANONYMOUS_NAME, null, mine);  // 익명이면 profileId도 숨긴다
        }
        return new AuthorResponse(String.valueOf(profile.getId()), profile.getNickname(), null, mine);
    }
}
