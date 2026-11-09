package com.dangdang.domain.reading.dto.request;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.enums.ReadingType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// 혈당 기록 등록 요청
public record ReadingCreateRequest(
        @NotNull(message = "혈당 수치를 입력해 주세요.")
        @Min(value = 20, message = "혈당은 20~600 사이로 입력해 주세요.")
        @Max(value = 600, message = "혈당은 20~600 사이로 입력해 주세요.")
        Integer value,

        @NotNull(message = "잰 시각을 입력해 주세요.")
        Instant measuredAt,          // JSON은 "2026-11-09T08:30:00+09:00" 또는 "2026-11-08T23:30:00Z"

        @NotNull(message = "공복·식후·기타 중 하나를 골라 주세요.")
        ReadingType readingType,

        MeasureSlot slot) {          // 없어도 됨
}
