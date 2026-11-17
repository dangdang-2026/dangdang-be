package com.dangdang.domain.postpartum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// 바꿀 칸만 보낸다 (안 보낸 칸은 그대로)
public record PostpartumTestUpdateRequest(
        @Schema(example = "당화혈색소 검사")
        @Size(max = 40, message = "검사 이름은 40자까지 적을 수 있어요.")
        @Pattern(regexp = ".*\\S.*", message = "검사 이름을 적어 주세요.")
        String name,
        @Schema(example = "2026-12-27") LocalDate testDate,
        Boolean remindDayBefore,
        Boolean remindSameDay) {
}
