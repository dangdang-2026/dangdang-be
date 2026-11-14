package com.dangdang.domain.report.enums;

// U-12 신고 사유 6개
public enum ReportReason {
    MEDICAL_ADVICE,  // 진단&처방, 약 이름&용량 권유
    NUMBER_COMPARE,  // 혈당 수치 비교·순위
    AD_SALES,        // 광고&판매&후원 요청
    PRIVACY,         // 개인정보 노출
    ABUSE,           // 비방&혐오&성적인 글
    ETC              // 기타
}
