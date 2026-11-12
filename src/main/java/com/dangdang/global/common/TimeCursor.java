package com.dangdang.global.common;

import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.model.BaseErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

// "시각 + id" 커서. 프론트는 안을 들여다보지 않고 받은 글자를 그대로 돌려준다
public record TimeCursor(Instant at, long id) {

    // filter = 검색 조건. 다른 조건에서 만든 커서를 쓰면 decode가 막는다
    public String encode(String filter) {
        String raw = at + "|" + id + "|" + Integer.toHexString(filter.hashCode());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TimeCursor decode(String encoded, String filter, BaseErrorCode invalidCode) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3 || !parts[2].equals(Integer.toHexString(filter.hashCode()))) {
                throw new CustomException(invalidCode);
            }
            return new TimeCursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw new CustomException(invalidCode);
        }
    }
}
