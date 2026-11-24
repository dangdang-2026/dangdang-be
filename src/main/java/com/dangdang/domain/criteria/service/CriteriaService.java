package com.dangdang.domain.criteria.service;

import com.dangdang.domain.criteria.dto.request.CriteriaVersionCreateRequest;
import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.repository.CriteriaVersionRepository;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CriteriaService {

    // 다음 기준이 없을 때 다시 붙이는 끝 (끝 없음 — 미래 시각으로 적은 기록도 그날 기준을 찾으면 이 기준이라)
    private static final Instant NO_NEXT_CRITERIA = Instant.parse("9999-01-01T00:00:00Z");

    private final CriteriaVersionRepository criteriaVersionRepository;
    private final UserRepository userRepository;
    private final ReadingRepository readingRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    // 등록 — UPDATE가 아니라 항상 새 줄
    // 지난 날짜(어제 이전)부터 적용이면 그 사이 이미 잰 기록도 이 기준으로 다시 붙임 (C-09 「10월 19일부터 적용」)
    @Transactional
    public CriteriaVersionResponse create(Long userId, CriteriaVersionCreateRequest req) {
        // 적용일: 요청 값이 있으면 그것, 비었으면 한국 오늘 (시계가 한국 시간대)
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveFrom = req.effectiveFrom() != null ? req.effectiveFrom() : today;
        boolean backdated = effectiveFrom.isBefore(today);
        if (backdated) {
            // 기록을 다시 붙일 땐 이 사람 줄을 먼저 FOR UPDATE로 잠금 (동의 확인의 FOR SHARE보다 먼저 — 둘이 FOR SHARE를 잡은 채
            // 서로 FOR UPDATE를 기다리지 않게). 혈당 저장·수정·되돌리기는 FOR SHARE를 잡으니 다시 붙이는 동안 기다렸다가
            // 새 기준을 보고, 먼저 잡은 저장이 있으면 그게 끝난 뒤 그 기록까지 다시 붙임. 지난 날짜 기준 두 개가 동시에 와도 차례로
            userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        }
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        User user = findUser(userId);
        CriteriaVersion saved = criteriaVersionRepository.save(CriteriaVersion.of(
                user, req.postMealMinutes(), req.measureSlots(), req.fastingTarget(), req.postMealTarget(),
                req.sourceNote(), effectiveFrom, clock.instant()));
        if (backdated) {
            relinkReadings(userId, saved);
        }
        return CriteriaVersionResponse.from(saved);
    }

    // 적용일 0시 ~ 다음 기준 시작 전(없으면 끝까지)에 잰 기록을 새 기준으로
    // 오늘·미래부터 적용이면 부르지 않음: 오늘 진료 전에 잰 기록은 그때 기준 그대로 (C-10 「지난 기록은 그때 기준으로」)
    // 같은 적용일 기준이 이미 있으면 새 것이 이김 (지금 기준 찾기의 「적용일 늦은 순 → id 큰 순」과 같은 규칙)
    private void relinkReadings(Long userId, CriteriaVersion saved) {
        Instant from = KoreanTime.startOfDay(saved.getEffectiveFrom());
        Instant to = criteriaVersionRepository
                .findFirstByUserIdAndEffectiveFromGreaterThanOrderByEffectiveFromAscIdAsc(userId, saved.getEffectiveFrom())
                .map(next -> KoreanTime.startOfDay(next.getEffectiveFrom()))
                .orElse(NO_NEXT_CRITERIA);
        readingRepository.relinkCriteria(userId, saved, from, to);
    }

    // 이력 — 최신 먼저
    public List<CriteriaVersionResponse> history(Long userId) {
        return criteriaVersionRepository.findAllByUserIdOrderByEffectiveFromDescIdDesc(userId).stream()
                .map(CriteriaVersionResponse::from)
                .toList();
    }

    // 지금 기준 — 없으면 null (컨트롤러가 그대로 돌려주면 응답에서 data가 빠짐)
    public CriteriaVersionResponse current(Long userId) {
        return criteriaVersionRepository
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(userId, LocalDate.now(clock))
                .map(CriteriaVersionResponse::from)
                .orElse(null);
    }

    // 오늘 기준 (없으면 빈 기준)
    @Transactional
    public CriteriaVersion currentOrEmpty(Long userId) {
        return onDateOrEmpty(userId, LocalDate.now(clock));
    }

    // 혈당 저장에서 부르는 함수 — 그 날짜에 적용 중이던 기준이 있으면 그것, 없으면 빈 기준을 저장해서 돌려준다
    @Transactional
    public CriteriaVersion onDateOrEmpty(Long userId, LocalDate date) {
        return criteriaVersionRepository
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(userId, date)
                .orElseGet(() -> criteriaVersionRepository.save(
                        CriteriaVersion.empty(findUser(userId), date, clock.instant())));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }
}