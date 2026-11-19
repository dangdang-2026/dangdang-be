package com.dangdang.domain.announcement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 소식·광고 카드를 거는 자리. maxShown = 그 자리에 한 번에 내려주는 최대 개수 (우선순위 순으로 앞에서부터)
@Getter
@RequiredArgsConstructor
public enum AnnouncementSlot {
    SPLASH(3),     // 재실행 스플래시 카드 (AD-01 · S-01b·S-01c). 프론트가 하루 1번 앞에서부터 보여 준다
    COMMUNITY(1);  // 커뮤니티 메인 광고 1칸 (AD-02)

    private final int maxShown;
}
