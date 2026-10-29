package com.dangdang.domain.criteria.service;

import com.dangdang.domain.criteria.dto.request.CriteriaVersionCreateRequest;
import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.repository.CriteriaVersionRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CriteriaService {

    private final CriteriaVersionRepository criteriaVersionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // 등록 — UPDATE가 아니라 항상 새 줄
    @Transactional
    public CriteriaVersionResponse create(Long userId, CriteriaVersionCreateRequest req) {
        User user = findUser(userId);
        // 적용일: 요청 값이 있으면 그것, 비었으면 한국 오늘
        LocalDate effectiveFrom = req.effectiveFrom() != null ? req.effectiveFrom() : LocalDate.now(clock);
        CriteriaVersion saved = criteriaVersionRepository.save(CriteriaVersion.of(
                user, req.postMealMinutes(), req.measureSlots(), req.fastingTarget(), req.postMealTarget(),
                req.sourceNote(), effectiveFrom, clock.instant()));
        return CriteriaVersionResponse.from(saved);
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

    // 혈당 저장에서 부르는 함수
    @Transactional
    public CriteriaVersion currentOrEmpty(Long userId) {
        LocalDate today = LocalDate.now(clock);
        // 지금 기준이 있으면 그것, 없으면 빈 기준을 저장해서 돌려준다
        return criteriaVersionRepository
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(userId, today)
                .orElseGet(() -> criteriaVersionRepository.save(
                        CriteriaVersion.empty(findUser(userId), today, clock.instant())));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
    }
}