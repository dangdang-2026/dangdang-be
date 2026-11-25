package com.dangdang.domain.community.dto.response;

import com.dangdang.domain.community.entity.CommunityProfile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

// 글·댓글에 붙는 글쓴이. 이메일·실명은 절대 담지 않는다
public record AuthorResponse(
        @Schema(description = "프로필 번호. 익명·탈퇴면 null", example = "102") String profileId,
        @Schema(description = "닉네임. 익명이면 「익명 산모」, 탈퇴면 「탈퇴한 회원」", example = "말랑한 두부") String displayName,
        @Schema(description = "주수 배지 「임신 27주」·「산후 3개월」·「산후 1개월 미만」. 익명·배지 끔·임신 정보 없음·다른 상황이면 null",
                example = "임신 27주") String weekBadge,
        boolean isMine) {

    private static final String ANONYMOUS_NAME = "익명 산모";
    private static final String WITHDRAWN_NAME = "탈퇴한 회원";

    // 배지 없이 (베스트 댓글 한 줄 등)
    public static AuthorResponse of(CommunityProfile profile, boolean anonymous, Long myProfileId) {
        return of(profile, anonymous, myProfileId, Map.of());
    }

    // badges: profileId → 주수 배지 (WeekBadges)
    public static AuthorResponse of(CommunityProfile profile, boolean anonymous, Long myProfileId,
                                    Map<Long, String> badges) {
        if (profile == null) {
            return new AuthorResponse(null, WITHDRAWN_NAME, null, false);
        }
        boolean mine = profile.getId().equals(myProfileId);
        if (anonymous) {
            return new AuthorResponse(null, ANONYMOUS_NAME, null, mine);  // 익명이면 profileId도 배지도 숨긴다
        }
        return new AuthorResponse(String.valueOf(profile.getId()), profile.getNickname(),
                badges.get(profile.getId()), mine);
    }
}
