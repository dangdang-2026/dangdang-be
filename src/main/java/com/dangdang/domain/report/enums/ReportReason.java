package com.dangdang.domain.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// U-12 신고 사유 8개 = 운영자 가리기 사유 = 이용 제한 사유 (하나의 목록, A-02·A-03·U-12·U-14)
// label은 서버가 직접 보내는 글(가림 알림, 운영자 묶음 75 C)에 쓸 이름이다. 화면 칩 문구는 프론트가 정한다
// DB(reports.reason VARCHAR(30))와 JSON에는 이름(name)이 그대로 들어간다. 값을 빼거나 이름을 바꾸면 예전 줄을 못 읽는다
@Getter
@RequiredArgsConstructor
public enum ReportReason {
    MEDICAL_ADVICE("잘못된 의료 정보(용량·치료 지시)"),  // 진단&처방, 약 이름&용량 권유
    NUMBER_COMPARE("혈당 수치 비교"),                    // 혈당 수치 비교·순위
    AD_SALES("광고·판매"),                               // 광고&판매&후원 요청
    PRIVACY("개인정보 노출"),                            // 개인정보 노출
    ABUSE("욕설·비방"),                                  // 비방&혐오&성적인 글
    CRISIS("걱정되는 글(자해·위급)"),                     // 처리함 맨 위 (U-13b)
    MISINFO("잘못된 건강 정보"),                          // 약·용량 말고, 예: 「탄수화물 끊으세요」
    ETC("기타");                                         // 기타

    private final String label;
}
