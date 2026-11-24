package com.dangdang.domain.chat.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

// 답할 수 없을 때 이유 (AI가 고름). 화면이 버튼을 고를 때 씀 (61번 6-B · 7-4)
public enum ChatUnknownReason {
    JUDGMENT,    // 괜찮은지·용량·먹어도 되는지 판단 요구 → 「물어볼 말로 정리하기」
    NO_SOURCE,   // 자료에 없음 → 「물어볼 말로 정리하기」
    CHITCHAT,    // 인사·감사 → 버튼 없음
    RUDE,        // 놀림 → 버튼 없음
    OFF_TOPIC,   // 상관없는 질문 → 버튼 없음
    NO_CONSENT,  // 기록 동의 없음 → 「AI 설정으로」
    DISTRESS,    // 우울·지침·외로움 → 1577-0199 · 물어볼 말로 정리
    APP_HELP,    // 앱 사용법 → 「도움말로」
    ABOUT,       // 당당이 소개·개인정보 → 「개인정보 처리방침」
    // ↓ 이 셋은 저장하기 전에 위급 안내(RISK)로 바뀐다 (RiskSignalRules.forAiReason) → 응답 reason에는 안 나옴
    URGENT,
    CRISIS,
    SAFETY;

    // AI가 이상한 값을 주거나 안 주면 NO_SOURCE (예전 동작과 같음)
    // JSONB에서 읽을 때도 이걸 쓴다 → 나중에 이름을 빼도 예전 대화 목록이 500 나지 않는다
    @JsonCreator
    public static ChatUnknownReason from(String value) {
        if (value == null) {
            return NO_SOURCE;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return NO_SOURCE;
        }
    }

    public boolean isRisk() {
        return this == URGENT || this == CRISIS || this == SAFETY;
    }
}
