package com.dangdang.global.ai;

import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.global.config.ClockConfig;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

// AI 호출 문지기 (SYS-03): 1) 동의 확인 2) 하루 상한 3) 호출 번호(requestId) 붙이고 로그 — 글 내용은 로그에 안 남긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGuard {

    private final ConsentService consentService;
    private final AiUsageStore usageStore;
    private final Clock clock;

    public AiCall begin(Long userId, AiFeature feature) {
        if (!consentService.isGranted(userId, feature.requiredConsent())) {
            throw new CustomException(AiErrorCode.CONSENT_REQUIRED);
        }
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.KST));
        long used = usageStore.increase(feature, userId, today);
        if (used > feature.getDailyLimit()) {
            usageStore.decrease(feature, userId, today);   // 거절한 것은 세지 않는다
            throw new CustomException(AiErrorCode.DAILY_LIMIT);
        }
        AiCall call = new AiCall(UUID.randomUUID().toString(), feature, userId, today, clock.millis());
        log.info("[AI] 시작 feature={} user={} requestId={} 오늘 {}/{}", feature, userId, call.requestId(),
                used, feature.getDailyLimit());
        return call;
    }

    // 끝나면 한 줄: 어떤 프롬프트 판으로 무슨 결과였는지, 몇 ms (값은 안 남김)
    public void finish(AiCall call, String promptVersion, String outcome) {
        log.info("[AI] 끝 feature={} user={} requestId={} promptVersion={} outcome={} {}ms", call.feature(),
                call.userId(), call.requestId(), promptVersion, outcome, clock.millis() - call.startedAtMillis());
    }

    // AI 서버가 실패했으면 횟수를 돌려준다
    public void fail(AiCall call, String reason) {
        usageStore.decrease(call.feature(), call.userId(), call.day());
        log.warn("[AI] 실패 feature={} user={} requestId={} reason={} {}ms", call.feature(), call.userId(),
                call.requestId(), reason, clock.millis() - call.startedAtMillis());
    }

    public record AiCall(String requestId, AiFeature feature, Long userId, LocalDate day, long startedAtMillis) {
    }
}
