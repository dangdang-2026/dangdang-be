package com.dangdang.domain.reading.dto.request;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.enums.MeterLimit;
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

        ReadingType readingType,     // 식후 → 공복·기타로 바꾸면서 mealId를 안 보내면 식사 연결이 끊김 (D-05)

        MeasureSlot slot,

        Long mealId,                 // 다른 식사로 바꿀 때

        MeterLimit meterLimit,       // HI·LO로 고칠 때. value와 같이 오면 400

        Instant expectedUpdatedAt) { // 화면을 열 때 받은 이 기록의 updatedAt (선택). 그 사이 다른 곳에서 고쳤으면 409 (#172)

    // 바꿀 값이 하나도 없는 요청인지 (expectedUpdatedAt은 바꿀 값이 아님)
    public boolean isEmpty() {
        return value == null && measuredAt == null && readingType == null && slot == null && mealId == null
                && meterLimit == null;
    }

    // 식후 혈당이 식사 시작 뒤인지 다시 봐야 하는 칸(잰 시각·식사·잰 때)을 보냈는지
    public boolean changesMealTiming() {
        return measuredAt != null || mealId != null || readingType != null;
    }

    // 어느 알림에 붙을지가 바뀔 수 있는 칸(식사·잰 때·시점·잰 시각)을 보냈는지. 값만 고치면 알림은 그대로
    public boolean changesReminderLink() {
        return mealId != null || readingType != null || slot != null || measuredAt != null;
    }

    // 숫자와 HI·LO를 같이 보냈는지 (어느 쪽으로 고칠지 모름 → 400)
    public boolean hasBothValueAndMeterLimit() {
        return value != null && meterLimit != null;
    }
}
