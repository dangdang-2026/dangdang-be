package com.dangdang.domain.push.repository;

import com.dangdang.domain.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    // "내 것만" — id가 있어도 다른 사람 것이면 비어서 나온다
    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);

    // 살아 있는 구독 전부 (revoked_at IS NULL)
    List<PushSubscription> findAllByUserIdAndRevokedAtIsNull(Long userId);
}
