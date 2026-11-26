package com.dangdang.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 운영자 화면 오른쪽 위 계정 칸 (운영 v2 · 헤더)
public record AdminMeResponse(
        Long userId,
        @Schema(example = "당당 운영진") String displayName,
        @Schema(example = "admin@dang-dang.site") String email) {
}
