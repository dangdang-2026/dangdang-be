package com.dangdang.domain.reading.service;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.repository.CriteriaVersionRepository;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reminder.entity.Reminder;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.global.common.KoreanTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// 식후 혈당마다 "재야 할 시각"을 한 번에 구함 (오늘 화면 · 기록 목록 · 같은 메뉴 지난 기록 · 혈당 저장·수정·상세 응답이 같이 씀)
// 1순위 이 혈당이 끝낸 알림의 예정 시각, 2순위 식사 시작 + 그 날짜 병원 식후 시간 (ReadingEvaluator.expectedAt)
// 부르는 쪽 트랜잭션 안에서 돈다
@Component
@RequiredArgsConstructor
public class ExpectedTimeResolver {

    private final ReminderRepository reminderRepository;
    private final MealRepository mealRepository;
    private final CriteriaVersionRepository criteriaVersionRepository;

    // readingId → 재야 할 시각. 식후가 아니거나 모르면 map에 없음 (get하면 null)
    // knownMeals = 부르는 쪽이 이미 읽어 둔 살아 있는 식사 (또 조회하지 않게). 없으면 List.of()
    public Map<Long, Instant> expectedAtByReading(Long userId, List<Reading> readings, List<Meal> knownMeals) {
        List<Reading> postMeal = readings.stream()
                .filter(r -> r.getReadingType() == ReadingType.POST_MEAL)
                .toList();
        if (postMeal.isEmpty()) {
            return Map.of();
        }

        // 이 혈당이 끝낸 측정 알림의 예정 시각 (reminders.reading_id는 unique라 혈당 하나에 알림 하나)
        Map<Long, Instant> reminderAtByReading = reminderRepository
                .findByReadingIdIn(postMeal.stream().map(Reading::getId).toList()).stream()
                .collect(Collectors.toMap(Reminder::getReadingId, Reminder::getScheduledAt));

        Map<Long, Meal> mealsById = liveMealsOf(userId, postMeal, knownMeals);
        Map<LocalDate, Optional<Integer>> postMealMinutesByDate = new HashMap<>();   // 같은 날짜 기준을 또 조회하지 않게

        Map<Long, Instant> result = new HashMap<>();
        for (Reading r : postMeal) {
            Meal meal = r.getMealId() == null ? null : mealsById.get(r.getMealId());
            Instant mealStartedAt = meal == null ? null : meal.getStartedAt();
            Integer postMealMinutes = meal == null ? null : postMealMinutesByDate
                    .computeIfAbsent(KoreanTime.dateOf(mealStartedAt), date -> postMealMinutesOn(userId, date))
                    .orElse(null);
            Instant expectedAt = ReadingEvaluator.expectedAt(
                    r, reminderAtByReading.get(r.getId()), mealStartedAt, postMealMinutes);
            if (expectedAt != null) {
                result.put(r.getId(), expectedAt);
            }
        }
        return result;
    }

    // 하나만 (혈당 저장·수정·상세 응답용)
    public Instant expectedAtOf(Long userId, Reading reading) {
        return expectedAtByReading(userId, List.of(reading), List.of()).get(reading.getId());
    }

    // 식후 혈당이 붙은 식사 중 살아 있는 것만 (지운 식사에 붙은 혈당은 재야 할 시각을 모르는 것으로 봄)
    // 이미 읽어 둔 식사는 그대로 쓰고, 없는 것(어젯밤 식사 등)만 DB에서 찾음
    private Map<Long, Meal> liveMealsOf(Long userId, List<Reading> postMeal, List<Meal> knownMeals) {
        // 아래에서 put을 하니까 고칠 수 있는 HashMap으로 만듦 (toMap 결과는 고칠 수 있다는 보장이 없음)
        Map<Long, Meal> mealsById = new HashMap<>(knownMeals.stream().collect(Collectors.toMap(Meal::getId, m -> m)));
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
