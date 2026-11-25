package com.dangdang.domain.reading.dto.request;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.reading.enums.MeterLimit;
import com.dangdang.domain.reading.enums.ReadingType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;

// 혈당 기록 등록 요청. 숫자(value)와 HI·LO(meterLimit) 중 하나만 보냄
public record ReadingCreateRequest(
        @Min(value = 20, message = "혈당은 20~600 사이로 입력해 주세요.")
        @Max(value = 600, message = "혈당은 20~600 사이로 입력해 주세요.")
        Integer value,               // 숫자로 적을 때. HI·LO면 안 보냄

        @NotNull(message = "잰 시각을 입력해 주세요.")
        @PastOrPresent(message = "잰 시각은 지금보다 뒤일 수 없어요.")
        Instant measuredAt,          // JSON은 "2026-11-09T08:30:00+09:00" 또는 "2026-11-08T23:30:00Z"

        @NotNull(message = "공복·식후·기타 중 하나를 골라 주세요.")
        ReadingType readingType,

        MeasureSlot slot,            // 없어도 됨

        Long mealId,                 // 어떤 식사의 식후 혈당인지 (없어도 됨, 내 식사만)

        MeterLimit meterLimit) {     // 혈당계에 HI·LO가 떴을 때 (G-11). 숫자면 안 보냄

    // 숫자와 HI·LO 중 딱 하나만 왔는지 (둘 다·둘 다 없음 → 400)
    public boolean hasOneValue() {
        return (value == null) != (meterLimit == null);
    }

    // 저장할 숫자. HI·LO면 혈당계 끝값(600·20)
    public Integer valueToSave() {
        return meterLimit != null ? meterLimit.value() : value;
    }
}
