package com.dangdang.global.common;

import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// 주소 뒤 ?from=...&page=... 값을 글자로 받아 직접 검사한다 (틀리면 전부 400 GLOBAL_40001)
public final class QueryParams {

    private QueryParams() {
    }

    public static LocalDate requiredDate(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + " 값을 보내 주세요.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, name + "은(는) yyyy-MM-dd 형식으로 보내 주세요.");
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
