package com.dangdang.global.ai;

import com.dangdang.domain.user.enums.ConsentType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// OpenAI(국외)로 글을 보내는 AI 기능과 하루 상한 (SYS-03). 사진(AI_PHOTO)은 국내 서버라 여기 없다
@Getter
@RequiredArgsConstructor
public enum AiFeature {
    CHAT("챗봇", 30),          // 당당이에게 물어보기 (Q)
    VISIT_NOTE("진료 메모", 10); // 진료 메모 AI 정리 (C-11·C-12)

    private final String label;
    private final int dailyLimit;

    // 국외 이전(N-05)에 동의해야 OpenAI로 보낼 수 있다. 거부해도 기록·알림은 그대로 (LEGAL-05)
    public ConsentType requiredConsent() {
        return ConsentType.OVERSEAS_TRANSFER;
    }
}
