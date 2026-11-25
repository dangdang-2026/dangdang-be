package com.dangdang.domain.mealplan.service;

import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.mealplan.dto.request.MealPlanRequest;
import com.dangdang.domain.mealplan.dto.response.MealPlanResponse;
import com.dangdang.domain.mealplan.entity.MealPlan;
import com.dangdang.domain.mealplan.repository.MealPlanRepository;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// 내 식사 안내 (M-15). 한 사람 한 장 — 처음 저장하면 만들고, 다음부터는 바꾼다
@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final CareSiteRepository careSiteRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    // 없으면 null → 화면은 「내 식사 안내 보기」 줄을 숨긴다
    @Transactional(readOnly = true)
    public MealPlanResponse get(Long userId) {
        return mealPlanRepository.findByUserId(userId).map(p -> toResponse(userId, p)).orElse(null);
    }

    @Transactional
    public MealPlanResponse save(Long userId, MealPlanRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        if (req.careSiteId() != null
                && careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(req.careSiteId(), userId).isEmpty()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT);   // 남의 병원·지운 병원
        }
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        }
        mealPlanRepository.upsert(userId, req.careSiteId(), req.receivedOn(),
                strip(req.mealGuide()), strip(req.snackGuide()), clock.instant());
        MealPlan plan = mealPlanRepository.findByUserId(userId).orElseThrow();
        return toResponse(userId, plan);
    }

    @Transactional
    public void delete(Long userId) {
        mealPlanRepository.findByUserId(userId).ifPresent(mealPlanRepository::delete);
    }

    // 병원은 soft delete라 DB의 ON DELETE SET NULL이 안 돈다
    // → 지운 병원이면 번호는 숨기고(화면이 404 안 나게) 이름은 남긴다 (C-20 「‘받은 곳’은 그대로 남아요」)
    private MealPlanResponse toResponse(Long userId, MealPlan p) {
        if (p.getCareSiteId() == null) {
            return MealPlanResponse.from(p, null, null);
        }
        return careSiteRepository.findByIdAndUserId(p.getCareSiteId(), userId)
                .map(site -> MealPlanResponse.from(p, site.getDeletedAt() == null ? site.getId() : null, site.getName()))
                .orElseGet(() -> MealPlanResponse.from(p, null, null));
    }

    private static String strip(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
