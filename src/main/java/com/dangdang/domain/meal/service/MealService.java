package com.dangdang.domain.meal.service;

import com.dangdang.domain.meal.dto.request.MealCreateRequest;
import com.dangdang.domain.meal.dto.request.MealUpdateRequest;
import com.dangdang.domain.meal.dto.response.MealPageResponse;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.exception.MealErrorCode;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reminder.service.ReminderService;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealService {

    private final MealRepository mealRepository;
    private final UserRepository userRepository;
    private final ReminderService reminderService;
    private final Clock clock;

    private static final int MAX_PAGE_SIZE = 100;

    // 식사 시작 기록
    @Transactional
    public MealResponse create(Long userId, MealCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Meal saved = mealRepository.save(Meal.of(
                user, req.mealSlot(), req.startedAt(), req.isStartedOffline(), req.note()));
        // 식후 측정 알림 예약. 간식이거나 병원 기준(식후 n분·잴 때)이 없으면 예약 안 함
        reminderService.scheduleAfterMeal(userId, saved.getId(), saved.getMealSlot(), saved.getStartedAt());
        return MealResponse.from(saved);
    }

    // 상세
    public MealResponse get(Long userId, Long mealId) {
        return MealResponse.from(findMine(userId, mealId));
    }

    // 목록 (기간 + 커서). from·to는 한국 날짜, 둘 다 포함. 첫 페이지는 cursorAt·cursorId 없이 부름
    public MealPageResponse list(Long userId, LocalDate from, LocalDate to,
                                 Instant cursorAt, Long cursorId, int size) {
        if (from.isAfter(to)) {
            throw new CustomException(MealErrorCode.INVALID_DATE_RANGE);
        }
        if ((cursorAt == null) != (cursorId == null)) {
            throw new CustomException(MealErrorCode.INVALID_CURSOR);
        }
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);   // 0이나 10000이 와도 1~100 사이로 맞춤

        Instant fromAt = startOfKoreanDay(from);
        // 첫 페이지: 커서 = 기간 끝(to 다음 날 0시), id = 0 → 기간 끝 시각과 딱 같은 식사는 안 걸림
        Instant afterAt = cursorAt != null ? cursorAt : startOfKoreanDay(to.plusDays(1));
        Long afterId = cursorId != null ? cursorId : 0L;

        // 하나 더 받아서 다음 페이지가 있는지 확인함 (count 쿼리 안 씀)
        List<Meal> fetched = mealRepository.findPage(
                userId, fromAt, afterAt, afterId, Limit.of(pageSize + 1));
        return MealPageResponse.of(fetched, pageSize);
    }

    // 수정 (보낸 칸만)
    @Transactional
    public MealResponse update(Long userId, Long mealId, MealUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(MealErrorCode.NOTHING_TO_UPDATE);
        }
        Meal meal = findMine(userId, mealId);
        boolean timingChanged = (req.mealSlot() != null && req.mealSlot() != meal.getMealSlot())
                || (req.startedAt() != null && !req.startedAt().equals(meal.getStartedAt()));
        meal.update(req.mealSlot(), req.startedAt(), req.note());
        if (timingChanged) {   // 끼니나 시작 시각이 바뀌면 알림 시각·종류도 바뀜. 메모만 바꾸면 그대로 둠
            reminderService.rescheduleAfterMeal(userId, mealId, meal.getMealSlot(), meal.getStartedAt());
        }
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담으려고 먼저 DB에 씀
        mealRepository.flush();
        return MealResponse.from(meal);
    }

    // 삭제 (soft delete). 이미 지운 식사를 또 지우면 findMine에서 404
    @Transactional
    public void delete(Long userId, Long mealId) {
        findMine(userId, mealId).delete(clock.instant());   // 변경 감지로 트랜잭션 끝날 때 반영됨
        reminderService.cancelForMeal(mealId);               // 지운 식사의 식후 알림이 울리지 않게 함
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    private Meal findMine(Long userId, Long mealId) {
        return mealRepository.findByIdAndUserIdAndDeletedAtIsNull(mealId, userId)
                .orElseThrow(() -> new CustomException(MealErrorCode.MEAL_NOT_FOUND));
    }

    // 한국 날짜 -> 그날 0시(한국)의 세계 공통 시각
    private Instant startOfKoreanDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }
}
