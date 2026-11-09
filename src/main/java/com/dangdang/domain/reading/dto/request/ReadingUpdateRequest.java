package com.dangdang.domain.reading.dto.request;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.enums.ReadingType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;

// 혈당 기록 수정 요청 (PATCH) — 보낸 칸만 바뀜, 안 보낸 칸(null)은 그대로 둠
public record ReadingUpdateRequest(
        @Min(value = 20, message = "혈당은 20~600 사이로 입력해 주세요.")
        @Max(value = 600, message = "혈당은 20~600 사이로 입력해 주세요.")
        Integer value,

        @PastOrPresent(message = "잰 시각은 지금보다 뒤일 수 없어요.")
        Instant measuredAt,          // 등록과 같은 형식 ("2026-11-09T08:30:00+09:00")

        ReadingType readingType,

        MeasureSlot slot) {

    // 바꿀 값이 하나도 없는 요청인지
    public boolean isEmpty() {
        return value == null && measuredAt == null && readingType == null && slot == null;
    }
}
