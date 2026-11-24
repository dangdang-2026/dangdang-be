package com.dangdang.domain.pregnancy.service;

import com.dangdang.domain.postpartum.dto.response.PostpartumTestResponse;
import com.dangdang.domain.postpartum.repository.PostpartumTestRepository;
import com.dangdang.domain.pregnancy.dto.response.GraduationResponse;
import com.dangdang.domain.pregnancy.dto.response.PostpartumHomeResponse;
import com.dangdang.domain.pregnancy.dto.response.PregnancyStateResponse;
import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.exception.PregnancyErrorCode;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.config.ClockConfig;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 입원 / 다른 상황 / 출산·되돌리기 / 산후 / 졸업 (TODAY-10 · PP-01·03·02 · E-03b · E-06). 앱은 검사 시기를 정하지 않는다 (R6)
@Slf4j
@Service
@RequiredArgsConstructor
public class PostpartumService {

    private final PregnancyRepository pregnancyRepository;
    private final ReminderRepository reminderRepository;
    private final PostpartumTestRepository postpartumTestRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    // E-03 입원했어요 / E-04 다시 켜기
    @Transactional
    public PregnancyStateResponse hospitalization(Long userId, boolean hospitalized) {
        lockUser(userId);
        Instant now = clock.instant();
        Pregnancy p = activeOrStart(userId, now);
        int paused = 0;
        if (hospitalized) {
            if (p.getState() != PregnancyState.PREGNANT && p.getState() != PregnancyState.HOSPITALIZED) {
                throw new CustomException(PregnancyErrorCode.WRONG_STATE);   // 산후에는 입원 버튼이 없다
            }
            p.hospitalize(now);
            paused = cancelScheduled(userId);
        } else {
            if (p.getState() != PregnancyState.HOSPITALIZED) {
                throw new CustomException(PregnancyErrorCode.WRONG_STATE);
            }
            p.leaveHospital(now);
        }
        log.info("[산후] 입원 {} - 사용자: {}, 멈춘 알림: {}", hospitalized ? "시작" : "끝", userId, paused);
        return PregnancyStateResponse.of(p, paused);
    }

    // E-03 「다른 상황이에요」 / E-03b 「다시 켜기」 — 측정 알림만 멈춘다 (기록은 그대로, 무슨 일인지 안 물음)
    // 멈추기는 임신 중·입원 중·이미 멈춤에서. 산후엔 측정 알림이 이미 멈춰 있어서 409
    @Transactional
    public PregnancyStateResponse measurementPause(Long userId, boolean paused) {
        lockUser(userId);
        Instant now = clock.instant();
        if (!paused) {
            Pregnancy p = active(userId);
            if (p == null && pregnancyRepository.existsByUserIdAndState(userId, PregnancyState.ENDED)) {
                throw new CustomException(PregnancyErrorCode.ALREADY_GRADUATED);
            }
            if (p == null || p.getState() != PregnancyState.PAUSED) {
                throw new CustomException(PregnancyErrorCode.WRONG_STATE);   // 멈춘 상태가 아닌데 다시 켜기
            }
            p.resume(now);
            log.info("[산후] 알림 멈춤 끝 - 사용자: {}", userId);
            return PregnancyStateResponse.of(p, 0);
        }
        Pregnancy p = activeOrStart(userId, now);
        if (p.getState() == PregnancyState.POSTPARTUM) {
            throw new CustomException(PregnancyErrorCode.WRONG_STATE);
        }
        p.pause(now);
        int cancelled = cancelScheduled(userId);
        log.info("[산후] 알림 멈춤 시작 - 사용자: {}, 멈춘 알림: {}", userId, cancelled);
        return PregnancyStateResponse.of(p, cancelled);
    }

    // E-06 출산일 → 산후 모드. 측정 알림 멈춤, 임신 중 기록은 그대로
    @Transactional
    public PregnancyStateResponse deliver(Long userId, LocalDate deliveryDate) {
        lockUser(userId);
        if (deliveryDate.isAfter(today())) {
            throw new CustomException(PregnancyErrorCode.FUTURE_DELIVERY_DATE);
        }
        Instant now = clock.instant();
        Pregnancy p = activeOrStart(userId, now);
        p.deliver(deliveryDate, now);
        int paused = cancelScheduled(userId);
        log.info("[산후] 출산일 저장 - 사용자: {}, 멈춘 알림: {}", userId, paused);
        return PregnancyStateResponse.of(p, paused);
    }

    // E-06 · E-07 「되돌리기」 — 산후 → 임신 중 (산후인 동안 언제든). 출산일·다시 물어볼 날을 지운다
    // 측정 알림은 다음 예약부터 다시 간다 (공복·자기 전은 스케줄러가 다시 만들고, 식후는 다음 식사부터. 취소된 알림은 안 살아남)
    // 등록한 산후 검사 일정은 지우지 않지만, 산후가 아닌 동안 검사 알림은 보내지 않는다 (PostpartumTestRepository)
    @Transactional
    public PregnancyStateResponse undoDelivery(Long userId) {
        lockUser(userId);
        Pregnancy p = postpartum(userId);   // 산후가 아니면 409 PREGNANCY_40901
        p.undoDelivery(clock.instant());
        log.info("[산후] 출산 되돌리기 - 사용자: {}", userId);
        return PregnancyStateResponse.of(p, 0);
    }

    // E-09 나중에 다시 물어보기 — 같은 질문은 하나만 (덮어쓰기)
    @Transactional
    public LocalDate remindLater(Long userId, LocalDate remindOn) {
        lockUser(userId);
        if (remindOn.isBefore(today())) {
            throw new CustomException(PregnancyErrorCode.PAST_ASK_DATE);
        }
        Pregnancy p = postpartum(userId);
        p.askLater(remindOn, clock.instant());
        return p.getPostpartumAskOn();
    }

    // E-07 · E-10 산후 홈
    @Transactional(readOnly = true)
    public PostpartumHomeResponse home(Long userId) {
        Pregnancy p = postpartum(userId);
        LocalDate today = today();
        List<PostpartumTestResponse> upcoming = postpartumTestRepository
                .findByUserIdAndTestDateGreaterThanEqualOrderByTestDateAscIdAsc(userId, today).stream()
                .map(PostpartumTestResponse::from).toList();
        boolean askToday = upcoming.isEmpty() && p.getPostpartumAskOn() != null
                && !p.getPostpartumAskOn().isAfter(today);
        long day = ChronoUnit.DAYS.between(p.getDeliveryDate(), today) + 1;
        return new PostpartumHomeResponse(p.getDeliveryDate(), day, p.getPostpartumAskOn(), askToday, upcoming);
    }

    // E-12 임신 중 관리 졸업 — 산후에서만 (「다른 상황」·입원 중에는 열리지 않음)
    @Transactional
    public GraduationResponse graduate(Long userId) {
        lockUser(userId);
        Pregnancy p = postpartum(userId);
        User user = p.getUser();
        Instant now = clock.instant();
        p.graduate(now);
        long weeks = Math.max(1, Duration.between(user.getCreatedAt(), now).toDays() / 7);
        long days = Math.max(1, ChronoUnit.DAYS.between(KoreanTime.dateOf(user.getCreatedAt()), today()) + 1);
        return new GraduationResponse(pregnancyRepository.countReadings(userId), weeks, days);
    }

    // 이 사람 줄을 잠그고 건강정보 동의를 본다 — 입원·출산·졸업 같은 상태 바꾸기가 동시에 와도 차례로 (예정일 저장도 같은 잠금)
    // 임신 줄이 없을 때 둘이 동시에 새 줄을 만들다 ux_pregnancies_active에 걸리는 일도 없다
    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403 (위에서 이미 더 센 잠금을 잡음)
    }

    private Pregnancy postpartum(Long userId) {
        Pregnancy p = active(userId);
        if (p == null || p.getState() != PregnancyState.POSTPARTUM) {
            throw new CustomException(PregnancyErrorCode.WRONG_STATE);
        }
        return p;
    }

    private Pregnancy active(Long userId) {
        return pregnancyRepository.findByUserIdAndStateNot(userId, PregnancyState.ENDED).orElse(null);
    }

    // 예정일을 안 적은 사람도 입원·출산·다른 상황은 누를 수 있다 → 그때 한 줄 만든다. 졸업한 사람은 안 됨
    private Pregnancy activeOrStart(Long userId, Instant now) {
        Pregnancy p = active(userId);
        if (p != null) {
            return p;
        }
        if (pregnancyRepository.existsByUserIdAndState(userId, PregnancyState.ENDED)) {
            throw new CustomException(PregnancyErrorCode.ALREADY_GRADUATED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return pregnancyRepository.save(Pregnancy.start(user, null, now));
    }

    // 아직 안 보낸 측정 알림은 취소 (이미 보낸 것·끝난 것은 그대로)
    private int cancelScheduled(Long userId) {
        return reminderRepository.cancelScheduledForUser(userId);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ClockConfig.KST));
    }
}
