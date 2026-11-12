package com.dangdang.domain.user.service;

import com.dangdang.domain.user.dto.request.NotificationPreferenceUpdateRequest;
import com.dangdang.domain.user.dto.response.NotificationPreferenceResponse;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserRepository userRepository;

    // 줄이 없어도 기본값으로 답한다 (읽기만으로 줄을 만들지 않는다, updatedAt은 null)
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse get(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .map(NotificationPreferenceResponse::from)
                .orElseGet(() -> NotificationPreferenceResponse.from(NotificationPreference.defaultsFor(null)));
    }

    @Transactional
    public NotificationPreferenceResponse update(Long userId, NotificationPreferenceUpdateRequest req) {
        if (req.hasNoField()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "바꿀 설정을 하나 이상 보내 주세요.");
        }
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> notificationPreferenceRepository.save(NotificationPreference.defaultsFor(findUser(userId))));
        preference.update(req.getMeasurementEnabled(), req.getScheduleEnabled(), req.getHideContent(), req.getRenotifyEnabled(),
                req.getFastingAlarmTime(), req.getBedtimeAlarmTime());
        notificationPreferenceRepository.flush();   // updatedAt을 응답에 담으려고 바로 저장한다
        return NotificationPreferenceResponse.from(preference);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }
}
