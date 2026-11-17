package com.dangdang.domain.postpartum.dto.response;

import com.dangdang.domain.postpartum.entity.PostpartumTest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record PostpartumTestResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "75g 경구 당부하 검사") String name,
        @Schema(example = "2026-12-20") LocalDate testDate,
        boolean remindDayBefore,
        boolean remindSameDay,
        @Schema(description = "결과 메모 (없으면 null)") String resultNote) {

    public static PostpartumTestResponse from(PostpartumTest t) {
        return new PostpartumTestResponse(t.getId(), t.getName(), t.getTestDate(), t.isRemindDayBefore(),
                t.isRemindSameDay(), t.getResultNote());
    }
}
