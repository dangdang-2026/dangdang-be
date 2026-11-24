package com.dangdang.domain.reading.service;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.exception.MealErrorCode;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.reminder.service.ReminderService;
import com.dangdang.domain.reading.dto.request.ReadingCreateRequest;
import com.dangdang.domain.reading.dto.request.ReadingUpdateRequest;
import com.dangdang.domain.reading.dto.response.ReadingPageResponse;
import com.dangdang.domain.reading.dto.response.ReadingResponse;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.enums.ReadingType;
import com.dangdang.domain.reading.exception.ReadingErrorCode;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.common.UndoWindow;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingService {

    private final ReadingRepository readingRepository;
    private final UserRepository userRepository;
    private final CriteriaService criteriaService;
    private final MealRepository mealRepository;
    private final ReminderService reminderService;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final ExpectedTimeResolver expectedTimeResolver;

    private static final int MAX_PAGE_SIZE = 100;

    // 등록 — 잰 날짜(한국)에 적용 중이던 병원 기준을 붙여서 저장. 숫자와 HI·LO 중 하나만
    @Transactional
    public ReadingResponse create(Long userId, ReadingCreateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        if (!req.hasOneValue()) {
            throw new CustomException(ReadingErrorCode.VALUE_OR_METER_LIMIT);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        Meal meal = mealIfMine(userId, req.mealId());   // 식후 시각 확인·재야 할 시각에 그대로 씀 (또 조회하지 않게)
        CriteriaVersion criteria = criteriaService.onDateOrEmpty(userId, KoreanTime.dateOf(req.measuredAt()));
        Reading reading = Reading.of(
                user, criteria, req.readingType(), req.slot(), req.measuredAt(), req.valueToSave(),
                req.meterLimit(), req.mealId(), clock.instant());
        checkMeasuredAfterMeal(reading, meal);   // 저장 전에 (G-10)
        Reading saved = readingRepository.save(reading);
        if (saved.getMealId() != null) {   // 식후 혈당을 쟀으니 그 식사의 "재세요" 알림은 끝 처리
            reminderService.completeForMeal(saved.getMealId(), saved.getId());
        } else if (saved.getSlot() != null) {   // 공복, 자기 전 혈당이면 그날 그 알림을 끝 처리
            reminderService.completeForSlot(userId, saved.getSlot(), saved.getMeasuredAt(), saved.getId());
        }
        // 알림 끝 처리 뒤에 구해서, 방금 끝낸 알림의 예정 시각이 재야 할 시각 1순위로 잡힘 (G-07 「늦게 잰 기록」)
        return ReadingResponse.of(saved, expectedTimeResolver.expectedAtOf(userId, saved, meal));
    }

    // 상세
    public ReadingResponse get(Long userId, Long readingId) {
        Reading reading = findMine(userId, readingId);
        return ReadingResponse.of(reading, expectedTimeResolver.expectedAtOf(userId, reading, null));
    }

    // 목록 (기간 + 커서). from·to는 한국 날짜, 둘 다 포함. 첫 페이지는 cursorAt·cursorId 없이 부름
    public ReadingPageResponse list(Long userId, LocalDate from, LocalDate to,
                                    Instant cursorAt, Long cursorId, int size) {
        if (from.isAfter(to)) {
            throw new CustomException(ReadingErrorCode.INVALID_DATE_RANGE);
        }
        if ((cursorAt == null) != (cursorId == null)) {
            throw new CustomException(ReadingErrorCode.INVALID_CURSOR);
        }
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);   // 0이나 10000이 와도 1~100 사이로 맞춤

        Instant fromAt = KoreanTime.startOfDay(from);
        // 첫 페이지: 커서 = 기간 끝(to 다음 날 0시) → 쿼리의 "커서보다 과거" 조건이 기간 끝 역할도 함
        Instant afterAt = cursorAt != null ? cursorAt : KoreanTime.endOfDay(to);
        // id는 1부터라 0이면 "기간 끝과 같은 시각" 기록이 안 걸림 (MAX_VALUE면 다음 날 0시 기록이 섞여 들어옴)
        Long afterId = cursorId != null ? cursorId : 0L;

        // 하나 더 받아서 다음 페이지가 있는지 확인함 (count 쿼리 안 씀)
        List<Reading> fetched = readingRepository.findPage(
                userId, fromAt, afterAt, afterId, Limit.of(pageSize + 1));
        return ReadingPageResponse.of(fetched, pageSize);
    }

    // 수정 (보낸 칸만). 잰 시각이 바뀌면 바뀐 날짜(한국)의 병원 기준을 다시 붙임
    @Transactional
    public ReadingResponse update(Long userId, Long readingId, ReadingUpdateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        if (req.isEmpty()) {
            throw new CustomException(ReadingErrorCode.NOTHING_TO_UPDATE);
        }
        if (req.hasBothValueAndMeterLimit()) {
            throw new CustomException(ReadingErrorCode.VALUE_OR_METER_LIMIT);
        }
        Reading reading = findMine(userId, readingId);
        Meal meal = mealIfMine(userId, req.mealId());
        CriteriaVersion criteria = req.measuredAt() != null
                ? criteriaService.onDateOrEmpty(userId, KoreanTime.dateOf(req.measuredAt()))
                : null;   // 잰 시각을 안 바꾸면 기준도 그대로

        reading.update(req.value(), req.meterLimit(), req.measuredAt(), criteria, req.readingType(), req.slot(),
                req.mealId(), clock.instant());
        // 잰 시각·식사·잰 때를 바꿀 때만 확인함 (G-10). 던지면 위 변경은 트랜잭션째 되돌아감
        // 값만 고칠 땐 안 봄 — 저장 뒤 식사 시작을 늦춰 둔 기록이면 값 하나 고치려다 400이 나서
        if (req.changesMealTiming()) {
            if (meal == null) meal = liveMealOf(userId, reading.getMealId());   // 식사는 그대로 두고 시각·잰 때만 바꾼 경우
            checkMeasuredAfterMeal(reading, meal);
        }
        try {
            if (req.changesReminderLink()) {   // 식사·시점·잰 때·시각이 바뀌면 예전 알림은 풀고 지금 맞는 알림을 끝 처리
                reminderService.relinkAfterEdit(userId, reading.getId(), reading.getMealId(), reading.getSlot(),
                        reading.getMeasuredAt());
            }
            // save 안 불러도 트랜잭션 끝날 때 바뀐 칸이 DB에 반영됨(변경 감지).
            // 다만 updatedAt은 DB에 쓸 때 채워져서, 응답에 새 값을 담으려고 여기서 먼저 반영함
            // 여기서 먼저 써야 동시 수정 충돌(row_version)도 409로 바꿔 줄 수 있음
            readingRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CustomException(ReadingErrorCode.VERSION_CONFLICT);   // 읽은 뒤 다른 요청이 먼저 고치거나 지움
        }
        return ReadingResponse.of(reading, expectedTimeResolver.expectedAtOf(userId, reading, meal));
    }

    // 삭제 (soft delete). 이미 지운 기록을 또 지우면 findMine에서 404
    @Transactional
    public void delete(Long userId, Long readingId) {
        findMine(userId, readingId).delete(clock.instant());
        try {
            readingRepository.flush();   // 동시 수정 충돌을 409로 바꾸려고 트랜잭션 끝나기 전에 씀
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CustomException(ReadingErrorCode.VERSION_CONFLICT);
        }
    }

    // 되돌리기 (D-08). 지울 때 알림·식사 연결은 안 건드렸으니 deleted_at만 비우면 끝 (editedAt도 그대로 → 「수정됨」 아님)
    // 조건부 UPDATE 한 번이라 동시에 두 번 눌러도 하나만 성공, 나머지는 409
    @Transactional
    public ReadingResponse restore(Long userId, Long readingId) {
        sensitiveConsentGuard.require(userId);   // 되살리기도 건강 기록 저장이라 동의가 없으면 403
        if (readingRepository.restore(readingId, userId, UndoWindow.since(clock.instant())) == 0) {
            throw restoreFailure(userId, readingId);
        }
        Reading reading = findMine(userId, readingId);   // 벌크 UPDATE가 영속성 컨텍스트를 비워서 DB에서 새로 읽음
        return ReadingResponse.of(reading, expectedTimeResolver.expectedAtOf(userId, reading, null));
    }

    // 왜 못 되돌렸는지: 없음·남의 것 404 / 안 지움 409 / 5분 지남 409
    private CustomException restoreFailure(Long userId, Long readingId) {
        return readingRepository.findByIdAndUserId(readingId, userId)
                .map(r -> r.getDeletedAt() == null
                        ? new CustomException(ReadingErrorCode.NOT_DELETED)
                        : new CustomException(ReadingErrorCode.UNDO_EXPIRED))
                .orElseGet(() -> new CustomException(ReadingErrorCode.READING_NOT_FOUND));
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404 (상세·수정·삭제가 같이 씀)
    private Reading findMine(Long userId, Long readingId) {
        return readingRepository.findByIdAndUserIdAndDeletedAtIsNull(readingId, userId)
                .orElseThrow(() -> new CustomException(ReadingErrorCode.READING_NOT_FOUND));
    }

    // 식후 혈당은 붙은 식사 시작보다 앞 시각일 수 없음 (G-10 「식사 시작 12:10부터 지금까지」)
    // meal = 혈당에 지금 붙은 살아 있는 식사. 공복·기타는 식사가 붙어 있어도 안 봄 (등록이 공복+식사를 허용해서)
    // 지운 식사에 붙은 채면(meal null) 비교할 식사가 없어 넘어감
    private static void checkMeasuredAfterMeal(Reading reading, Meal meal) {
        if (reading.getReadingType() != ReadingType.POST_MEAL || meal == null) {
            return;
        }
        if (reading.getMeasuredAt().isBefore(meal.getStartedAt())) {
            throw new CustomException(ReadingErrorCode.MEASURED_BEFORE_MEAL);
        }
    }

    // mealId를 보냈으면 내 식사 + 삭제 안 된 식사인지 확인하고 돌려줌. 아니면 404 (남의 식사가 있다는 것도 안 알려줌)
    // 안 보냈으면 null
    private Meal mealIfMine(Long userId, Long mealId) {
        if (mealId == null) {
            return null;
        }
        return mealRepository.findByIdAndUserIdAndDeletedAtIsNull(mealId, userId)
                .orElseThrow(() -> new CustomException(MealErrorCode.MEAL_NOT_FOUND));
    }

    // 혈당에 붙어 있는 식사 (지웠거나 안 붙었으면 null)
    private Meal liveMealOf(Long userId, Long mealId) {
        return mealId == null ? null : mealRepository.findByIdAndUserIdAndDeletedAtIsNull(mealId, userId).orElse(null);
    }
}
