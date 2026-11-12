package com.dangdang.domain.pregnancy.enums;

// 임신 진행 상태. ENDED만 "끝난 임신", 나머지는 전부 진행 중
public enum PregnancyState {
    PREGNANT,      // 임신 중
    HOSPITALIZED,  // 입원 중 (알림 멈춤)
    POSTPARTUM,    // 산후
    ENDED          // 끝남
}
