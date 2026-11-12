package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record NameUpdateResponse(
        @Schema(description = "바뀐 이름", example = "다현") String name,
        @Schema(description = "바뀐 뒤의 수정 버전 (BIGINT 문자열)", example = "1") String version) {

    public static NameUpdateResponse from(User user) {
        return new NameUpdateResponse(user.getDisplayName(), String.valueOf(user.getRowVersion()));
    }
}
