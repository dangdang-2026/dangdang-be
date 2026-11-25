package com.dangdang.global.ai;

import com.dangdang.domain.user.enums.ConsentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// OpenAI(국외)로 글을 보내는 AI 기능과 하루 상한 (SYS-03). 사진(AI_PHOTO)은 국내 서버라 여기 없다
// 커뮤니티 글 확인(COMMUNITY_AI_CHECK)은 막지 않고 GPT만 건너뛰어서 여기 없다 (PostPrecheckService → allowLlm)
@Getter
@RequiredArgsConstructor
public enum AiFeature {
    CHAT("챗봇", 30, ConsentType.OVERSEAS_TRANSFER),         // 당당이에게 물어보기 (Q) — 질문 글이 OpenAI(국외)로 가므로 국외 이전 동의 필수 (Q-01, 11/24)
    VISIT_NOTE("진료 메모", 10, ConsentType.OVERSEAS_TRANSFER); // 진료 메모 AI 정리 (C-11·C-12)

    private final String label;
    private final int dailyLimit;
    // 이 기능을 쓰려면 필요한 동의 (null = 필요 없음). AiGuard.begin이 ConsentService.isGranted(지금 버전)로 본다
    // 챗봇: 질문 글이 국외로 가서 국외 이전 동의 필수. 「내 기록」을 쓰는 답은 ChatService가 AI_CHAT_RECORDS로 한 번 더 막는다
    // 진료 메모: 메모(건강정보)를 OpenAI(국외)로 보내므로 국외 이전 동의가 있어야 한다 (N-05)
    private final ConsentType requiredConsent;
}
