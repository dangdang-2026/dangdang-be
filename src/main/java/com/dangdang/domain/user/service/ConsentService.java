package com.dangdang.domain.user.service;

import com.dangdang.domain.notification.entity.Notification;
import com.dangdang.domain.notification.service.NotificationService;
import com.dangdang.domain.user.dto.request.ConsentRequest;
import com.dangdang.domain.user.dto.response.ConsentResponse;
import com.dangdang.domain.user.entity.ConsentEvent;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.ConsentEventRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.ClockConfig;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {
    private static final DateTimeFormatter KOREAN_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN);
    private static final String MARKETING_DEEP_LINK = "/my";   // 광고 설정 화면 주소가 정해지면 바꾼다

    private final UserRepository userRepository;
    private final ConsentEventRepository consentEventRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public ConsentResponse save(Long userId, ConsentRequest request) {
        // ① 필수 항목을 false로 보내면 거절. 건강정보(SENSITIVE) 철회는 전용 API로만 (M-07b)
        //    — 실수로 SENSITIVE:false가 섞여 와도 건강 기록이 날아가면 안 되니까
        boolean refusesRequired =
                request.items().stream().anyMatch(item -> item.type().isRequired() && !item.granted());
        if (refusesRequired) {
            throw new CustomException(UserErrorCode.REQUIRED_CONSENT_REFUSED);
        }
        // 이 사람 줄을 잠그고 읽는다 — 동의 저장이 동시에 두 번 와도 「저장 전 광고 동의」를 차례로 봐서 알림함이 두 줄 생기지 않게
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        ConsentEvent marketingBefore = latestByType(userId).get(ConsentType.MARKETING);   // 저장 전 상태
        // ② 덮어쓰지 않고 한 줄씩 쌓는다. 버전은 서버가, 시각은 앱 Clock이 넣는다 (같이 보낸 항목은 같은 시각)
        Instant now = clock.instant();
        List<ConsentEvent> events =
                request.items().stream()
                        .map(item -> ConsentEvent.of(user, item.type(), item.type().getVersion(), item.granted(), now))
                        .toList();
        consentEventRepository.saveAll(events);
        recordMarketingChange(user, marketingBefore, request, now);
        log.info("[Consent] 동의 저장 - 사용자: {}, 항목: {}개", userId, events.size());
        return getStatus(userId);
    }

    // N-09b 광고성 정보 동의가 실제로 바뀌었을 때만 알림함에 결과 한 줄 (처음부터 안 받음 → 안 받음은 안 남김)
    // 같은 트랜잭션에 남기고, 앱 안 배너(SSE)는 커밋 뒤에 간다 (NotificationService.record)
    private void recordMarketingChange(User user, ConsentEvent before, ConsentRequest request, Instant now) {
        request.items().stream()
                .filter(item -> item.type() == ConsentType.MARKETING)
                .reduce((first, second) -> second)                 // 같은 항목이 두 번 오면 마지막 것 (저장도 마지막이 최신)
                .ifPresent(item -> {
                    boolean wasGranted = before != null && before.isGranted();
                    if (wasGranted == item.granted()) {
                        return;
                    }
                    String title = item.granted() ? "광고성 정보 수신에 동의했어요" : "광고성 정보 수신을 거부했어요";
                    String body = KOREAN_DATE.format(now.atZone(ClockConfig.KST)) + " · 보낸 곳 당당";
                    notificationService.record(Notification.marketing(user, title, body, MARKETING_DEEP_LINK, now));
                });
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

    // 이 항목에 「지금 버전으로」 동의한 상태인지 (가장 최근 줄 기준. 한 번도 안 골랐으면 false)
    // AI 문지기(AiGuard)·사진 AI·챗봇 기록·커뮤니티 AI 확인이 쓴다. 문서 버전을 올리면 다시 받기 전까지 false
    @Transactional(readOnly = true)
    public boolean isGranted(Long userId, ConsentType type) {
        ConsentEvent latest = latestByType(userId).get(type);
        return latest != null && latest.isCurrentGrant();
    }

    // 가장 최근 줄 (버전 상관없이) — 철회 이력을 남길지 볼 때만 쓴다 (SensitiveConsentWithdrawalService)
    @Transactional(readOnly = true)
    public Optional<ConsentEvent> findLatest(Long userId, ConsentType type) {
        return Optional.ofNullable(latestByType(userId).get(type));
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

    // 필수 항목을 모두, 지금 버전으로 동의했는지 — 문서 버전을 올리면 예전 동의자는 A-01로 다시 간다 (78 E)
    // 건강정보 동의를 철회하면(M-07b) SENSITIVE 최신 줄이 false라 여기서 false → 다음에 열면 A-01 (74 A)
    private boolean isCompleted(Map<ConsentType, ConsentEvent> latest) {
        return Arrays.stream(ConsentType.values())
                .filter(ConsentType::isRequired)
                .allMatch(type -> latest.containsKey(type) && latest.get(type).isCurrentGrant());
    }
}
