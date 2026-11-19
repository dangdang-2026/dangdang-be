package com.dangdang.domain.card.enums;

import java.util.List;

// 카드뉴스 상태. 사용자에겐 PUBLISHED·CORRECTED·WITHDRAWN만 보인다
public enum CardStatus {
    DRAFT,       // 쓰는 중 (운영자만)
    PUBLISHED,   // 올림
    CORRECTED,   // 정정됨 (U-26b) — 정정 문구를 위에 보여 줌
    WITHDRAWN,   // 내려감 (U-26c) — 내용은 안 보여 줌
    RECHECK;     // 원문 다시 확인 중 (운영자만, 사용자에겐 그대로 PUBLISHED처럼)

    // 지금 올라가 있는 카드 — 카드 목록에 나오고, 소식 카드로 걸 수 있다 (내려간 카드·쓰는 중 카드는 빠짐)
    public static final List<CardStatus> LIVE = List.of(PUBLISHED, CORRECTED, RECHECK);
}
