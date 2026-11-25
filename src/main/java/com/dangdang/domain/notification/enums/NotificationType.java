package com.dangdang.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

//알림함 종류 (notifications.type 칸, VARCHAR(20))
// health: 건강 알림이라 건강정보 동의를 철회하면 같이 지운다 (SensitiveConsentWithdrawalService). 새 종류를 만들면 정해서 넣는다
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    MEASURE(true),            //혈당 측정
    VISIT_BEFORE(true),       //진료 전날
    VISIT_TODAY(true),        //진료 당일
    VISIT_NOTE(true),         //진료 후 메모
    POSTPARTUM(true);         //산후

    private final boolean health;
}
