package com.dangdang.domain.record.service;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.repository.CriteriaVersionRepository;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.push.service.PushSubscriptionService;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.reading.service.ReadingEvaluator;
import com.dangdang.domain.record.dto.response.TodayReadingResponse;
import com.dangdang.domain.record.dto.response.TodayResponse;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.domain.reminder.service.ReminderQueryService;
import com.dangdang.global.common.KoreanTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// 오늘 화면(홈) 조회. "오늘" = 한국 시각 00:00 ~ 다음 날 00:00
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayService {

    private final ReadingRepository readingRepository;
    private final MealRepository mealRepository;
    private final ReminderRepository reminderRepository;
    private final CriteriaVersionRepository criteriaVersionRepository;
    private final CriteriaService criteriaService;
    private final ReminderQueryService reminderQueryService;
    private final PushSubscriptionService pushSubscriptionService;
    private final Clock clock;

    public TodayResponse today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Instant start = KoreanTime.startOfDay(today);
        Instant end = KoreanTime.endOfDay(today);

        List<Meal> meals = mealRepository.findAllInRange(userId, start, end);
        List<Reading> readings = readingRepository.findAllInRange(userId, start, end);

        return new TodayResponse(
                today,
                criteriaService.current(userId),
                meals.stream().map(MealResponse::from).toList(),
                toReadingResponses(userId, readings, meals),
                reminderQueryService.findNext(userId),
                pushSubscriptionService.hasActive(userId));
    }

    // 혈당마다 재야 할 시각을 구해서 timing 계산에 넘김 (식후 혈당만 해당)
    private List<TodayReadingResponse> toReadingResponses(Long userId, List<Reading> readings, List<Meal> todayMeals) {
        List<Reading> postMeal = readings.stream()
                .filter(r -> r.getReadingType() == ReadingType.POST_MEAL)
                .toList();

        // 이 혈당이 끝낸 측정 알림의 예정 시각
        Map<Long, Instant> reminderAtByReading = postMeal.isEmpty() ? Map.of()
                : reminderRepository.findByReadingIdIn(postMeal.stream().map(Reading::getId).toList()).stream()
                        .collect(Collectors.toMap(Reminder::getReadingId, Reminder::getScheduledAt));

        Map<Long, Meal> mealsById = liveMealsOf(userId, postMeal, todayMeals);
        Map<LocalDate, Optional<Integer>> postMealMinutesByDate = new HashMap<>();   // 같은 날짜 기준을 또 조회하지 않게

        return readings.stream()
                .map(r -> {
                    Meal meal = r.getMealId() == null ? null : mealsById.get(r.getMealId());
                    Instant mealStartedAt = meal == null ? null : meal.getStartedAt();
                    Integer postMealMinutes = meal == null ? null : postMealMinutesByDate
                            .computeIfAbsent(KoreanTime.dateOf(mealStartedAt),
                                    date -> postMealMinutesOn(userId, date))
                            .orElse(null);
                    Instant expectedAt = ReadingEvaluator.expectedAt(
                            r, reminderAtByReading.get(r.getId()), mealStartedAt, postMealMinutes);
                    return TodayReadingResponse.of(r, expectedAt);
                })
                .toList();
    }

    // 식후 혈당이 붙은 식사 중 살아 있는 것만 (지운 식사에 붙은 혈당은 재야 할 시각을 모르는 것으로 봄)
    // 오늘 식사는 이미 불러왔으니 그대로 쓰고, 없는 것(어젯밤 식사 등)만 DB에서 찾음
    private Map<Long, Meal> liveMealsOf(Long userId, List<Reading> postMeal, List<Meal> todayMeals) {
        Map<Long, Meal> mealsById = todayMeals.stream().collect(Collectors.toMap(Meal::getId, m -> m));
        List<Long> missing = postMeal.stream()
                .map(Reading::getMealId)
                .filter(id -> id != null && !mealsById.containsKey(id))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            mealRepository.findAllByIdInAndUserIdAndDeletedAtIsNull(missing, userId)
                    .forEach(m -> mealsById.put(m.getId(), m));
        }
        return mealsById;
    }

    // 그 날짜(한국)에 적용 중이던 병원 식후 시간. 기준이 없거나 식후 시간을 안 적었으면 empty
    private Optional<Integer> postMealMinutesOn(Long userId, LocalDate date) {
        return criteriaVersionRepository
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(userId, date)
                .map(CriteriaVersion::getPostMealMinutes);
    }
}
