package com.dangdang.domain.community.dto.request;

// 보낸 칸만 바꾼다. 둘 다 비어 있으면 서비스가 400
public record CommunityProfileUpdateRequest(String nickname, Boolean showWeekBadge) {
}
