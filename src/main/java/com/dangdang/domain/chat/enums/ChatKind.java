package com.dangdang.domain.chat.enums;

// 답의 종류 = 화면이 무엇을 그릴지
public enum ChatKind {
    QUESTION,        // 내가 쓴 말
    RECORDS,         // Q-03 내 기록 카드 (숫자는 Spring이 계산)
    QUESTION_DRAFT,  // Q-04 물어볼 말 초안 → 「물어볼 것에 추가」
    INFO,            // Q-06 일반 정보 + 출처
    UNKNOWN,         // 자료에 없음 → 병원에 물어보세요
    RISK,            // Q-07 위험 신호 — AI를 거치지 않은 정해진 안내
    DOSE,            // 약·인슐린 용량 — AI를 거치지 않은 정해진 안내
    CONSENT_NEEDED,  // 기록을 보려면 기록 사용 동의가 필요
    FAILED           // Q-08 답을 만들지 못함
}
