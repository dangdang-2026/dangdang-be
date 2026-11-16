package com.dangdang.domain.record.service;

import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.record.dto.response.CalendarResponse;
import com.dangdang.domain.record.dto.response.RecordEventResponse;
import com.dangdang.domain.record.dto.response.RecordsResponse;
import com.dangdang.domain.record.enums.CalendarDayStatus;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 기록 탭 조회 (혈당·식사를 날짜별로 모아 보기, 한 달 달력). 날짜는 전부 한국 기준
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordService {

    // 한 번에 볼 수 있는 최대 기간 (주간 보기·한 달 보기까지 넉넉히)
    private static final int MAX_RANGE_DAYS = 31;

    // 같은 시각이면 종류(MEAL → READING) → id 순으로 줄 세움 (부를 때마다 순서가 안 바뀌게)
    private static final Comparator<RecordEventResponse> EVENT_ORDER =
            Comparator.comparing(RecordEventResponse::occurredAt)
                    .thenComparing(RecordEventResponse::type)
                    .thenComparing(RecordEventResponse::id);

    private final ReadingRepository readingRepository;
    private final MealRepository mealRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // 기록 통합 조회. from·to 둘 다 포함, 기록 없는 날도 days에 빈 events로 들어감
    public RecordsResponse records(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "from은 to보다 늦을 수 없어요.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "한 번에 " + MAX_RANGE_DAYS + "일까지 볼 수 있어요.");
        }
        Instant start = startOfDay(from);
        Instant end = startOfDay(to.plusDays(1));

        // 혈당·식사를 한 줄로 합친 뒤 한국 날짜별로 나눔
        Map<LocalDate, List<RecordEventResponse>> byDate = Stream.concat(
                        readingRepository.findAllInRange(userId, start, end).stream().map(RecordEventResponse::from),
                        mealRepository.findAllInRange(userId, start, end).stream().map(RecordEventResponse::from))
                .sorted(EVENT_ORDER)
                .collect(Collectors.groupingBy(e -> koreanDate(e.occurredAt())));

        List<RecordsResponse.Day> days = from.datesUntil(to.plusDays(1))
                .map(date -> new RecordsResponse.Day(date, byDate.getOrDefault(date, List.of())))
                .toList();
        return new RecordsResponse(from, to, days);
    }

    // 기록 달력 한 달. 상태 우선순위: 미래 → 오늘 → 가입 전 → 기록 있음 → 기록 없음
    // 가입 전 날짜로도 기록을 적을 수 있어서, "가입 전"의 경계는 가입일과 첫 기록일 중 더 이른 날로 잡음
    // (그래야 가입 전에 적은 기록이 흐리게 가려지지 않고, 그 달로 넘어갈 수도 있음)
    public CalendarResponse calendar(Long userId, YearMonth month) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        LocalDate firstRecordDate = firstRecordDate(userId);
        LocalDate signupDate = koreanDate(user.getCreatedAt());
        LocalDate startDate = firstRecordDate != null && firstRecordDate.isBefore(signupDate)
                ? firstRecordDate : signupDate;

        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Instant start = startOfDay(first);
        Instant end = startOfDay(last.plusDays(1));

        // 그 달에 기록이 있는 날짜들
        Set<LocalDate> recordedDates = new HashSet<>();
        readingRepository.findMeasuredAtsInRange(userId, start, end).forEach(at -> recordedDates.add(koreanDate(at)));
        mealRepository.findStartedAtsInRange(userId, start, end).forEach(at -> recordedDates.add(koreanDate(at)));

        List<CalendarResponse.Day> days = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            days.add(new CalendarResponse.Day(date, statusOf(date, today, startDate, recordedDates), false));
        }

        return new CalendarResponse(
                month,
                firstRecordDate,
                month.isAfter(YearMonth.from(startDate)),
                month.isBefore(YearMonth.from(today)),
                days);
    }

    // startDate = 가입일과 첫 기록일 중 더 이른 날
    private CalendarDayStatus statusOf(LocalDate date, LocalDate today, LocalDate startDate, Set<LocalDate> recordedDates) {
        if (date.isAfter(today)) return CalendarDayStatus.FUTURE;
        if (date.isEqual(today)) return CalendarDayStatus.TODAY;
        if (date.isBefore(startDate)) return CalendarDayStatus.BEFORE_SIGNUP;
        if (recordedDates.contains(date)) return CalendarDayStatus.RECORDED;
        return CalendarDayStatus.EMPTY;
    }

    // 혈당·식사 중 가장 먼저 남긴 기록의 한국 날짜. 둘 다 없으면 null
    private LocalDate firstRecordDate(Long userId) {
        Instant firstReading = readingRepository.findFirstMeasuredAt(userId);
        Instant firstMeal = mealRepository.findFirstStartedAt(userId);
        Instant first = Stream.of(firstReading, firstMeal)
                .filter(at -> at != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return first == null ? null : koreanDate(first);
    }

    // 세계 공통 시각 -> 한국 날짜
    private LocalDate koreanDate(Instant at) {
        return LocalDate.ofInstant(at, clock.getZone());
    }

    // 한국 날짜 -> 그날 0시(한국)의 세계 공통 시각
    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }
}
