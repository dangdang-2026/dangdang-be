package com.dangdang.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 동의 항목과 지금 문서 버전. 문서(약관·처리방침·동의 글)를 바꾸면 버전 글자만 올린다
// → 예전 버전 동의는 「동의 안 함」으로 보여서 다시 묻는다 (ConsentEvent.isCurrentGrant)
@Getter
@RequiredArgsConstructor
public enum ConsentType {
    AGE_14(true, "v1"),
    TERMS(true, "v1"),
    PRIVACY(true, "v1"),
    SENSITIVE(true, "v1"),               // 건강정보 — 끄는 길은 /api/me/consents/sensitive/withdraw 하나 (M-07b)
    MARKETING(false, "v1"),
    AI_PHOTO(false, "v1"),
    AI_CHAT_RECORDS(false, "v1"),        // 챗봇이 내 기록을 보고 답하기 — 건강정보 철회 때 같이 꺼진다
    OVERSEAS_TRANSFER(false, "v1"),      // 미국 OpenAI로 보내기 — 챗봇·진료 메모 정리의 문지기 (AiFeature)
    COMMUNITY_AI_CHECK(false, "v1");     // U-04 「확인했어요」 — 커뮤니티 글 약·용량 문장을 GPT(미국 OpenAI)로 확인 (83 H)

    private final boolean required;
    private final String version;
}
