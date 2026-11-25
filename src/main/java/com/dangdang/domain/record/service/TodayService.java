package com.dangdang.domain.record.service;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.insulin.dto.response.InsulinLogResponse;
import com.dangdang.domain.insulin.repository.InsulinLogRepository;
import com.dangdang.domain.meal.dto.response.MealItemSummaryResponse;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.meal.service.MealItemService;
import com.dangdang.domain.push.service.PushSubscriptionService;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.reading.service.ExpectedTimeResolver;
import com.dangdang.domain.record.dto.response.TodayReadingResponse;
import com.dangdang.domain.record.dto.response.TodayResponse;
import com.dangdang.domain.reminder.service.ReminderQueryService;
import com.dangdang.global.common.KoreanTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 오늘 화면(홈) 조회. "오늘" = 한국 시각 00:00 ~ 다음 날 00:00
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayService {

    private final ReadingRepository readingRepository;
    private final MealRepository mealRepository;
    private final MealItemService mealItemService;
    private final InsulinLogRepository insulinLogRepository;
    private final CriteriaService criteriaService;
    private final ReminderQueryService reminderQueryService;
    private final PushSubscriptionService pushSubscriptionService;
    private final ExpectedTimeResolver expectedTimeResolver;
    private final Clock clock;

    public TodayResponse today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Instant start = KoreanTime.startOfDay(today);
        Instant end = KoreanTime.endOfDay(today);

        List<Meal> meals = mealRepository.findAllInRange(userId, start, end);
        List<Reading> readings = readingRepository.findAllInRange(userId, start, end);

        return new TodayResponse(
                today,
                currentCriteria(userId),
                toMealResponses(meals),
                toReadingResponses(userId, readings, meals),
                insulinLogRepository.findAllInRange(userId, start, end).stream().map(InsulinLogResponse::from).toList(),
                reminderQueryService.findNext(userId),
                pushSubscriptionService.hasActive(userId));
    }

    // 오늘 식사 + 각 식사의 음식 항목 (항목은 한 번에 읽음)
    private List<MealResponse> toMealResponses(List<Meal> meals) {
        Map<Long, List<MealItemSummaryResponse>> items = mealItemService.itemsByMeal(meals.stream().map(Meal::getId).toList());
        return meals.stream().map(m -> MealResponse.from(m, items.getOrDefault(m.getId(), List.of()))).toList();
    }

    // 지금 병원 기준. 한 번도 안 적었으면 null
    // 기준 없이 혈당을 저장하면 빈 기준 줄이 자동으로 생기는데(CriteriaService.onDateOrEmpty),
    // 그건 사용자가 적은 기준이 아니라서 "기준 없음"(null)으로 봄 → 화면이 "병원 목표를 적어 주세요"를 띄울 수 있게
    private CriteriaVersionResponse currentCriteria(Long userId) {
        CriteriaVersionResponse c = criteriaService.current(userId);
        boolean empty = c != null
                && c.postMealMinutes() == null
                && c.fastingTarget() == null
                && c.postMealTarget() == null
                && (c.measureSlots() == null || c.measureSlots().isEmpty());
        return empty ? null : c;
    }

    // 혈당마다 재야 할 시각을 구해서 timing 계산에 넘김 (식후 혈당만 해당)
    private List<TodayReadingResponse> toReadingResponses(Long userId, List<Reading> readings, List<Meal> todayMeals) {
        Map<Long, Instant> expectedAt = expectedTimeResolver.expectedAtByReading(userId, readings, todayMeals);
        return readings.stream()
                .map(r -> TodayReadingResponse.of(r, expectedAt.get(r.getId())))
                .toList();
    }
}
