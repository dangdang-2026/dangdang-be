package com.dangdang.domain.report.enums;

// U-12 신고 사유 8개 = 운영자 가리기 사유(CRISIS 빼고) = 이용 제한 사유
// DB(reports.reason VARCHAR(30))와 JSON에는 이름이 그대로 들어간다. 값을 빼거나 이름을 바꾸면 예전 줄을 못 읽는다
public enum ReportReason {
    MEDICAL_ADVICE,  // 진단&처방, 약 이름&용량 권유
    NUMBER_COMPARE,  // 혈당 수치 비교·순위
    AD_SALES,        // 광고&판매&후원 요청
    PRIVACY,         // 개인정보 노출
    ABUSE,           // 비방&혐오&성적인 글
    CRISIS,          // 걱정되는 글(자해·위급). 처리함 맨 위 (U-13b). 가리기 사유로는 못 씀 (가릴 글이 아니라 연락할 사람)
    MISINFO,         // 잘못된 건강 정보 (약·용량 말고, 예: 「탄수화물 끊으세요」)
    ETC              // 기타
}
