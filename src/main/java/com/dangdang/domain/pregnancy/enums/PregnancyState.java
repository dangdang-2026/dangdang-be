package com.dangdang.domain.pregnancy.enums;

// 임신 진행 상태. ENDED만 "끝난 임신", 나머지는 전부 진행 중
public enum PregnancyState {
    PREGNANT,      // 임신 중
    HOSPITALIZED,  // 입원 중 (알림 멈춤)
    PAUSED,        // 다른 상황 — 알림만 멈춤 (E-03 「다른 상황이에요」). 무슨 일인지는 묻지도 저장하지도 않는다
    POSTPARTUM,    // 산후
    ENDED          // 끝남
}
