package com.dangdang.domain.reading.service;

import com.dangdang.domain.criteria.dto.response.CriteriaVersionResponse;
import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.enums.MealSlot;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reading.dto.response.ReadingStatsResponse;
import com.dangdang.domain.reading.dto.response.ReadingStatsResponse.MealAverage;
import com.dangdang.domain.reading.dto.response.ReadingStatsResponse.Point;
import com.dangdang.domain.reading.dto.response.ReadingStatsResponse.Range;
import com.dangdang.domain.reading.dto.response.ReadingStatsResponse.Summary;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.exception.ReadingErrorCode;
import com.dangdang.domain.reading.enums.StatsPeriod;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.reminder.enums.ReminderStatus;
import com.dangdang.domain.reminder.repository.ReminderRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// 혈당 통계 (D-17~D-21). 공복과 식후는 섞지 않고, 바로 앞 같은 길이 기간과 비교함
// "목표 안"은 기록마다 잴 때 적용 중이던 병원 기준으로 셈 (기준이 바뀐 날부터 새 기준)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingStatsService {

    // 기간 직접 고르기 최대 길이 (임신 기간 전체 280일 + 여유)
    private static final int MAX_CUSTOM_DAYS = 300;

    // "놓친 측정" = 알림을 보냈거나 열어 봤는데 혈당이 안 붙은 알림 (DONE이 안 됨)
    public static final List<ReminderStatus> MISSED_STATUSES = List.of(ReminderStatus.SENT, ReminderStatus.OPENED);
    // 알림이 가고 이만큼 지나야 "놓침"으로 셈 (늦게 적기 기준 30분과 같게)
    public static final Duration MISSED_GRACE = Duration.ofMinutes(30);
    private static final List<MeasureSlot> POST_MEAL_SLOTS =
            List.of(MeasureSlot.BREAKFAST_AFTER, MeasureSlot.LUNCH_AFTER, MeasureSlot.DINNER_AFTER);

    private final ReadingRepository readingRepository;
    private final MealRepository mealRepository;
    private final ReminderRepository reminderRepository;
    private final CriteriaService criteriaService;
    private final Clock clock;

    // type = FASTING 또는 POST_MEAL (API 파라미터 이름은 slot). from·to는 CUSTOM일 때만 씀 (다른 기간이면 무시)
    public ReadingStatsResponse stats(Long userId, ReadingType type, StatsPeriod period, LocalDate from, LocalDate to) {
        Range range = rangeOf(period, from, to);
        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        Range compareRange = new Range(range.from().minusDays(days), range.from().minusDays(1));

        // 두 기간을 한 번에 읽고 날짜로 나눔
        List<Reading> all = readingRepository.findAllInRangeByType(userId, type,
                KoreanTime.startOfDay(compareRange.from()), KoreanTime.endOfDay(range.to()));
        List<Reading> current = all.stream().filter(r -> !dateOf(r).isBefore(range.from())).toList();
        List<Reading> previous = all.stream().filter(r -> dateOf(r).isBefore(range.from())).toList();
        long previousMissed = missed(userId, type, compareRange);
        // 비교 기간에 기록도 놓친 측정도 없으면 비교 안 함 (D-20)
        // 놓친 측정만 있어도 비교함 — 지난번에 다 놓친 사람에게 "놓친 측정이 줄었어요"를 보여 줄 수 있게
        boolean hasPrevious = !previous.isEmpty() || previousMissed > 0;

        return new ReadingStatsResponse(
                type,
                range,
                compareRange,
                targetOf(criteriaService.current(userId), type),
                summarize(current, missed(userId, type, range)),
                hasPrevious ? summarize(previous, previousMissed) : null,
                series(current, range),
                hasPrevious ? series(previous, compareRange) : null,
                type == ReadingType.POST_MEAL ? byMeal(current, previous) : List.of());
    }

    // 보는 기간. 정해진 기간은 오늘까지 거꾸로, CUSTOM은 from·to 그대로 (둘 다 포함)
    private Range rangeOf(StatsPeriod period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        if (period != StatsPeriod.CUSTOM) {
            return new Range(today.minusDays(period.days() - 1L), today);
        }
        if (from == null || to == null) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "기간을 직접 고르면 from과 to를 같이 보내 주세요.");
        }
        if (from.isAfter(to)) {
            throw new CustomException(ReadingErrorCode.INVALID_DATE_RANGE);   // 기록 목록 API와 같은 코드
        }
        if (to.isAfter(today)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "to는 오늘보다 늦을 수 없어요.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_CUSTOM_DAYS) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "한 번에 " + MAX_CUSTOM_DAYS + "일까지 볼 수 있어요.");
        }
        return new Range(from, to);
    }

    // 지금 병원 목표 (공복이면 공복 목표, 식후면 식후 목표). 없으면 null
    private Integer targetOf(CriteriaVersionResponse criteria, ReadingType type) {
        if (criteria == null) {
            return null;
        }
        return type == ReadingType.FASTING ? criteria.fastingTarget() : criteria.postMealTarget();
    }

    private Summary summarize(List<Reading> readings, long missed) {
        // 목표가 있었던 기록만 "안/밖"을 셀 수 있음 (그때 기준에 목표가 없던 기록은 비율에서 뺌)
        List<Boolean> judged = readings.stream().map(ReadingEvaluator::aboveTarget).filter(Objects::nonNull).toList();
        Double inTargetRate = judged.isEmpty() ? null
                : round2((double) judged.stream().filter(above -> !above).count() / judged.size());
        int overDays = (int) readings.stream()
                .filter(r -> Boolean.TRUE.equals(ReadingEvaluator.aboveTarget(r)))
                .map(this::dateOf)
                .distinct()
                .count();
        return new Summary(average(readings), inTargetRate, readings.size(), missed, overDays);
    }

    // 날짜별 평균. 기록 없는 날도 넣어서 그래프 x축이 비지 않게 함
    private List<Point> series(List<Reading> readings, Range range) {
        Map<LocalDate, List<Reading>> byDate = readings.stream().collect(Collectors.groupingBy(this::dateOf));
        return range.from().datesUntil(range.to().plusDays(1))
                .map(date -> {
                    List<Reading> day = byDate.getOrDefault(date, List.of());
                    return new Point(date, average(day), day.size());
                })
                .toList();
    }

    // 끼니별 평균 (식후만). 끼니는 붙은 식사로 정하고, 식사가 없으면 잰 때(LUNCH_AFTER 등)로 정함
    // 어느 쪽도 모르는 기록은 끼니별 평균에서만 빠짐
    private List<MealAverage> byMeal(List<Reading> current, List<Reading> previous) {
        List<Long> mealIds = new ArrayList<>();
        current.forEach(r -> { if (r.getMealId() != null) mealIds.add(r.getMealId()); });
        previous.forEach(r -> { if (r.getMealId() != null) mealIds.add(r.getMealId()); });
        // 지운 식사도 "그때 무슨 끼니였는지"는 사실이라 같이 읽음 (혈당 기록은 살아 있으니까)
        Map<Long, MealSlot> mealSlotById = mealIds.isEmpty() ? Map.of()
                : mealRepository.findAllById(mealIds.stream().distinct().toList()).stream()
                        .collect(Collectors.toMap(Meal::getId, Meal::getMealSlot));
        Function<Reading, MealSlot> mealOf = r -> r.getMealId() != null && mealSlotById.containsKey(r.getMealId())
                ? mealSlotById.get(r.getMealId())
                : fromMeasureSlot(r.getSlot());

        Map<MealSlot, List<Reading>> cur = groupByMeal(current, mealOf);
        Map<MealSlot, List<Reading>> prev = groupByMeal(previous, mealOf);
        return Arrays.stream(MealSlot.values())   // 아침 → 점심 → 저녁 → 간식 순서
                .filter(meal -> cur.containsKey(meal) || prev.containsKey(meal))
                .map(meal -> new MealAverage(meal,
                        average(cur.getOrDefault(meal, List.of())),
                        average(prev.getOrDefault(meal, List.of()))))
                .toList();
    }

    private Map<MealSlot, List<Reading>> groupByMeal(List<Reading> readings, Function<Reading, MealSlot> mealOf) {
        return readings.stream()
                .filter(r -> mealOf.apply(r) != null)
                .collect(Collectors.groupingBy(mealOf));
    }

    private MealSlot fromMeasureSlot(MeasureSlot slot) {
        if (slot == null) return null;
        return switch (slot) {
            case BREAKFAST_AFTER -> MealSlot.BREAKFAST;
            case LUNCH_AFTER -> MealSlot.LUNCH;
            case DINNER_AFTER -> MealSlot.DINNER;
            default -> null;
        };
    }

    // 기간 안 놓친 측정 수. 공복은 공복 알림, 식후는 아침·점심·저녁 식후 알림만 셈
    // 알림 시각에서 MISSED_GRACE가 안 지난 알림은 아직 잴 시간이 있어서 안 셈 (방금 보낸 알림이 바로 "놓침"이 되지 않게)
    private long missed(Long userId, ReadingType type, Range range) {
        List<MeasureSlot> slots = type == ReadingType.FASTING ? List.of(MeasureSlot.FASTING) : POST_MEAL_SLOTS;
        Instant rangeEnd = KoreanTime.endOfDay(range.to());
        Instant graceEnd = clock.instant().minus(MISSED_GRACE);
        Instant end = rangeEnd.isBefore(graceEnd) ? rangeEnd : graceEnd;
        return reminderRepository.countByUserIdAndSlotInAndStatusInAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                userId, slots, MISSED_STATUSES, KoreanTime.startOfDay(range.from()), end);
    }

    // 평균 (반올림). 기록이 없으면 null
    private Integer average(List<Reading> readings) {
        if (readings.isEmpty()) {
            return null;
        }
        return (int) Math.round(readings.stream().mapToInt(Reading::getValue).average().orElseThrow());
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private LocalDate dateOf(Reading r) {
        return KoreanTime.dateOf(r.getMeasuredAt());
    }
}
