package com.dangdang.global.common;

import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

// 주소 뒤 ?from=...&page=... 값을 글자로 받아 직접 검사한다 (틀리면 전부 400 GLOBAL_40001)
public final class QueryParams {

    // 받을 수 있는 연도 범위. "+999999999-12-31" 같은 값도 날짜로 읽혀서,
    // 그대로 두면 하루 더하기(plusDays) 같은 계산에서 터져 500이 나기 때문에 미리 막음
    // 진료 날짜(VisitService)도 같은 범위를 씀
    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private QueryParams() {
    }

    public static LocalDate requiredDate(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + " 값을 보내 주세요.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + "은(는) yyyy-MM-dd 형식으로 보내 주세요.");
        }
        checkYear(date.getYear(), name);
        return date;
    }

    // 안 보내도 되는 날짜. 안 보냈으면 null, 보냈으면 requiredDate와 같은 검사
    public static LocalDate optionalDate(String value, String name) {
        return value == null || value.isBlank() ? null : requiredDate(value, name);
    }

    public static YearMonth requiredMonth(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + " 값을 보내 주세요.");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + "은(는) yyyy-MM 형식으로 보내 주세요.");
        }
        checkYear(month.getYear(), name);
        return month;
    }

    private static void checkYear(int year, String name) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT,
                    name + "은(는) " + MIN_YEAR + "~" + MAX_YEAR + "년 사이로 보내 주세요.");
        }
    }

    public static int intInRange(String value, int defaultValue, int min, int max, String name) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int number = Integer.parseInt(value);
            if (number >= min && number <= max) {
                return number;
            }
        } catch (NumberFormatException e) {
            // 아래 한 곳에서 같은 400으로 처리한다
        }
        throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + "은(는) " + min + "~" + max + " 사이 숫자로 보내 주세요.");
    }
}
