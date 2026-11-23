package com.dangdang.domain.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// U-17 「이용 제한 중」 화면
public record RestrictionResponse(
        @Schema(description = "지금 글·댓글 쓰기가 막혔는지", example = "true") boolean restricted,
        @Schema(description = "사유", example = "MEDICAL_ADVICE") String reason,
        @Schema(description = "풀리는 시각 (null = 운영자가 풀 때까지)") Instant endsAt,
        @Schema(description = "이 제한에 이미 이의 신청했는지 (true면 「이의 신청하기」 대신 「접수됨」)") boolean appealed) {

    public static RestrictionResponse none() {
        return new RestrictionResponse(false, null, null, false);
    }
}
