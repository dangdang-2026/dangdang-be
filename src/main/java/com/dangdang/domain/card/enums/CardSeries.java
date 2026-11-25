package com.dangdang.domain.card.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 카드 시리즈와 화면에 보이는 이름 (01_기획 「11_카드뉴스 발행 방식 조사」 제안 이름)
// DB에는 지금처럼 글자(series 칸)로 두고, 응답 때 이름만 붙인다
@Getter
@RequiredArgsConstructor
public enum CardSeries {
    BEFORE_VISIT("병원 가기 전에"),
    GUIDELINE("지침은 이렇게 말해요"),
    POSTPARTUM("출산 후에도"),
    MIND("마음 돌보기");

    private final String label;

    // 시리즈가 없거나 모르는 값이면 null
    public static String labelOf(String series) {
        if (series == null) {
            return null;
        }
        for (CardSeries s : values()) {
            if (s.name().equals(series)) {
                return s.label;
            }
        }
        return null;
    }
}
