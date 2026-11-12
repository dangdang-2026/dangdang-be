package com.dangdang.domain.community.dto.request;

import jakarta.validation.constraints.NotNull;

public record CommunityProfileCreateRequest(
        @NotNull(message = "닉네임을 적어 주세요.")
        String nickname,               // 2~10자 검사는 서비스의 NicknameRule 한 곳에서

        Boolean showWeekBadge) {       // 안 보내면 false
}
