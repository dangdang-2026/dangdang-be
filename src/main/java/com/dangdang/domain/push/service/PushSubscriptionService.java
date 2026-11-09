package com.dangdang.domain.push.service;

import com.dangdang.domain.push.dto.request.PushSubscriptionRequest;
import com.dangdang.domain.push.dto.response.PushSubscriptionResponse;
import com.dangdang.domain.push.entity.PushSubscription;
import com.dangdang.domain.push.exception.PushErrorCode;
import com.dangdang.domain.push.repository.PushSubscriptionRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // 같은 endpoint가 또 오면 새 줄 대신 그 줄을 다시 쓴다 (endpoint UNIQUE)
    @Transactional
    public PushSubscriptionResponse subscribe(Long userId, PushSubscriptionRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(req.endpoint())
                .orElse(null);
        if (subscription == null) {
            subscription = pushSubscriptionRepository.save(PushSubscription.of(user, req.endpoint(),
                    req.keys().p256dh(), req.keys().auth(), req.userAgent(), clock.instant()));
        } else {
            subscription.renew(user, req.keys().p256dh(), req.keys().auth(), req.userAgent());
        }
        return new PushSubscriptionResponse(subscription.getId());
    }

    // 사용자가 끊을 때 — 내 것이 아니면 있어도 404
    @Transactional
    public void unsubscribe(Long userId, Long subscriptionId) {
        PushSubscription subscription = pushSubscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new CustomException(PushErrorCode.SUBSCRIPTION_NOT_FOUND));
        subscription.revoke(clock.instant());
    }

    // 푸시 서버가 "죽은 구독"이라고 할 때 (404,410)
    @Transactional
    public void revokeGone(Long subscriptionId) {
        pushSubscriptionRepository.findById(subscriptionId)
                .ifPresent(subscription -> subscription.revoke(clock.instant()));
    }
}
