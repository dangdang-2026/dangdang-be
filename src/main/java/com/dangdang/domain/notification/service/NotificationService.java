package com.dangdang.domain.notification.service;

import com.dangdang.domain.notification.dto.response.NotificationPageResponse;
import com.dangdang.domain.notification.dto.response.NotificationReadResponse;
import com.dangdang.domain.notification.dto.response.NotificationResponse;
import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.exception.NotificationErrorCode;
import com.dangdang.domain.notification.repository.NotificationRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    // 최신이 위로. 잠금화면 숨김과 상관없이 본문을 그대로 준다 (앱 안은 로그인 상태)
    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id")));
        Slice<Notification> slice = notificationRepository.findByUserId(userId, pageable);
        return new NotificationPageResponse(slice.map(NotificationResponse::from).getContent(), page, size, slice.hasNext());
    }

    @Transactional
    public NotificationReadResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new CustomException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(clock.instant());
        return NotificationReadResponse.from(notification);
    }
}
