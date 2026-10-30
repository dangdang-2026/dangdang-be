package com.dangdang.domain.user.service;

import com.dangdang.domain.user.dto.request.ConsentRequest;
import com.dangdang.domain.user.dto.response.ConsentResponse;
import com.dangdang.domain.user.entity.ConsentEvent;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.ConsentEventRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {
    private final UserRepository userRepository;
    private final ConsentEventRepository consentEventRepository;

    @Transactional
    public ConsentResponse save(Long userId, ConsentRequest request) {
        // ① 필수 항목을 false로 보내면 거절 (그만 쓰려면 탈퇴)
        boolean refusesRequired =
                request.items().stream().anyMatch(item -> item.type().isRequired() && !item.granted());
        if (refusesRequired) {
            throw new CustomException(UserErrorCode.REQUIRED_CONSENT_REFUSED);
        }
        User user =
                userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        // ② 덮어쓰지 않고 한 줄씩 쌓는다. 버전은 서버가 넣는다
        List<ConsentEvent> events =
                request.items().stream()
                        .map(item -> ConsentEvent.of(user, item.type(), item.type().getVersion(), item.granted()))
                        .toList();
        consentEventRepository.saveAll(events);
        log.info("[Consent] 동의 저장 - 사용자: {}, 항목: {}개", userId, events.size());
        return getStatus(userId);
    }

    @Transactional(readOnly = true)
    public ConsentResponse getStatus(Long userId) {
        Map<ConsentType, ConsentEvent> latest = latestByType(userId);
        List<ConsentResponse.Item> items =
                Arrays.stream(ConsentType.values())
                        .map(type -> ConsentResponse.Item.of(type, latest.get(type)))
                        .toList();
        return new ConsentResponse(isCompleted(latest), items);
    }

    @Transactional(readOnly = true)
    public boolean isCompleted(Long userId) {
        return isCompleted(latestByType(userId));
    }

    // 항목마다 가장 최근 줄 하나만 남긴다 (최근 것부터 오니까 처음 본 것만 넣기)
    private Map<ConsentType, ConsentEvent> latestByType(Long userId) {
        Map<ConsentType, ConsentEvent> latest = new EnumMap<>(ConsentType.class);
        for (ConsentEvent event : consentEventRepository.findByUser_IdOrderByOccurredAtDescIdDesc(userId)) {
            latest.putIfAbsent(event.getConsentType(), event);
        }
        return latest;
    }

    private boolean isCompleted(Map<ConsentType, ConsentEvent> latest) {
        return Arrays.stream(ConsentType.values())
                .filter(ConsentType::isRequired)
                .allMatch(type -> latest.containsKey(type) && latest.get(type).isGranted());
    }
}
