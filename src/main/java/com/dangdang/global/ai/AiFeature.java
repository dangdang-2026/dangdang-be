package com.dangdang.global.ai;

import com.dangdang.domain.user.enums.ConsentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// OpenAI(국외)로 글을 보내는 AI 기능과 하루 상한 (SYS-03). 사진(AI_PHOTO)은 국내 서버라 여기 없다
@Getter
@RequiredArgsConstructor
public enum AiFeature {
    CHAT("챗봇", 30, null),                                   // 당당이에게 물어보기 (Q) — 일반 질문은 동의 없이 (11/20 결정)
    VISIT_NOTE("진료 메모", 10, ConsentType.OVERSEAS_TRANSFER); // 진료 메모 AI 정리 (C-11·C-12)

    private final String label;
    private final int dailyLimit;
    // 이 기능을 쓰려면 필요한 동의 (null = 필요 없음)
    // 챗봇: 일반 질문은 동의 없이 답한다. 「내 기록」을 쓰는 답은 ChatService가 AI_CHAT_RECORDS로 따로 막는다
    // 진료 메모: 메모(건강정보)를 OpenAI(국외)로 보내므로 국외 이전 동의가 있어야 한다 (N-05)
    private final ConsentType requiredConsent;
}
