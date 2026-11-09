package com.dangdang.domain.push.service;

import com.dangdang.domain.push.dto.response.PushDeliveryResponse;
import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.exception.PushErrorCode;
import com.dangdang.domain.push.infra.PushMessage;
import com.dangdang.domain.push.infra.WebPushSender;
import com.dangdang.domain.push.repository.PushSubscriptionRepository;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// @Transactional을 안붙인 이유 : 푸시 서버를 기다리는 동안 DB 연결을 붙잡지 않게
@Service
@RequiredArgsConstructor
public class PushSendService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushSubscriptionService pushSubscriptionService;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final WebPushSender webPushSender;

    // 다음 가이드의 스케줄러도 이 함수를 부른다
    public PushDeliveryResponse sendToUser(Long userId, PushMessage message) {
        List<PushSubscription> targets = pushSubscriptionRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        PushMessage toSend = hidePreview(userId) ? message.masked() : message;

        int sent = 0;
        int revoked = 0;
        int failed = 0;
        for (PushSubscription target : targets) {
            switch (webPushSender.send(target, toSend)) {
                case SENT -> sent++;
                case GONE -> {
                    pushSubscriptionService.revokeGone(target.getId());
                    revoked++;
                }
                case FAILED -> failed++;
            }
        }
        return new PushDeliveryResponse(sent, revoked, failed);
    }

    // MY-03 테스트 알림
    public PushDeliveryResponse sendTest(Long userId) {
        PushDeliveryResponse result = sendToUser(userId,
                new PushMessage("당당", "테스트 알림이에요. 잘 왔다면 설정 끝!", "/today", null));
        if (result.hadNoTarget()) {
            throw new CustomException(PushErrorCode.NO_ACTIVE_SUBSCRIPTION);
        }
        return result;
    }

    // 설정 줄이 없으면 가리는 쪽으로 (기본값 true와 같게)
    private boolean hidePreview(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreference::isHidePreview)
                .orElse(true);
    }
}
