package com.dangdang.domain.reading.enums;

import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;

import java.util.Arrays;

// 혈당 통계 기간 (D-17~D-21). 주소에는 "1W"처럼 숫자로 시작하는 값이 와서 code로 따로 받음
public enum StatsPeriod {
    ONE_WEEK("1W", 7),
    TWO_WEEKS("2W", 14),
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    CUSTOM("CUSTOM", 0);    // 기간 직접 고르기 (from·to 필수)

    private final String code;
    private final int days;   // 오늘 포함 며칠. CUSTOM은 from·to로 정해서 0

    StatsPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    public int days() {
        return days;
    }

    // "1W" -> ONE_WEEK. 안 보내면 1M(기본값), 모르는 값이면 400
    public static StatsPeriod fromCode(String value) {
        if (value == null || value.isBlank()) {
            return ONE_MONTH;
        }
        return Arrays.stream(values())
                .filter(p -> p.code.equals(value))
                .findFirst()
                .orElseThrow(() -> new CustomException(GlobalErrorCode.INVALID_INPUT,
                        "period는 1W·2W·1M·3M·CUSTOM 중 하나로 보내 주세요."));
    }
}
