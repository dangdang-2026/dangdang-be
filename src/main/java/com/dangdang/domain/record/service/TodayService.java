package com.dangdang.domain.record.service;

import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.dto.response.MealResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.push.service.PushSubscriptionService;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.record.dto.response.TodayReadingResponse;
import com.dangdang.domain.record.dto.response.TodayResponse;
import com.dangdang.domain.reminder.service.ReminderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 오늘 화면(홈) 조회. "오늘" = 한국 시각 00:00 ~ 다음 날 00:00
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayService {

    private final ReadingRepository readingRepository;
    private final MealRepository mealRepository;
    private final CriteriaService criteriaService;
    private final ReminderQueryService reminderQueryService;
    private final PushSubscriptionService pushSubscriptionService;
    private final Clock clock;

    public TodayResponse today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Instant start = today.atStartOfDay(clock.getZone()).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();

        List<Meal> meals = mealRepository.findAllInRange(userId, start, end);
        List<Reading> readings = readingRepository.findAllInRange(userId, start, end);

        return new TodayResponse(
                today,
                criteriaService.current(userId),
                meals.stream().map(MealResponse::from).toList(),
                toReadingResponses(readings),
                reminderQueryService.findNext(userId),
                pushSubscriptionService.hasActive(userId));
    }

    // 식후 혈당이 붙은 식사의 시작 시각을 찾아서 timing 계산에 넘김
    // 어젯밤 식사에 붙은 오늘 새벽 혈당도 있을 수 있어서, 오늘 식사 목록이 아니라 mealId로 한 번에 찾음
    private List<TodayReadingResponse> toReadingResponses(List<Reading> readings) {
        List<Long> mealIds = readings.stream().map(Reading::getMealId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Instant> mealStartedAt = mealRepository.findAllById(mealIds).stream()
                .collect(Collectors.toMap(Meal::getId, Meal::getStartedAt));
        Function<Reading, Instant> startedAtOf = r -> r.getMealId() == null ? null : mealStartedAt.get(r.getMealId());
        return readings.stream()
                .map(r -> TodayReadingResponse.of(r, startedAtOf.apply(r)))
                .toList();
    }
}
