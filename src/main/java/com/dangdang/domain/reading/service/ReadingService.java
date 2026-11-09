package com.dangdang.domain.reading.service;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.reading.dto.request.ReadingCreateRequest;
import com.dangdang.domain.reading.dto.response.ReadingPageResponse;
import com.dangdang.domain.reading.dto.response.ReadingResponse;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.exception.ReadingErrorCode;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
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
public class ReadingService {

    private final ReadingRepository readingRepository;
    private final UserRepository userRepository;
    private final CriteriaService criteriaService;
    private final Clock clock;

    private static final int MAX_PAGE_SIZE = 100;

    // 등록 — 잰 날짜(한국)에 적용 중이던 병원 기준을 붙여서 저장
    @Transactional
    public ReadingResponse create(Long userId, ReadingCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        CriteriaVersion criteria = criteriaService.onDateOrEmpty(userId, koreanDate(req.measuredAt()));
        Reading saved = readingRepository.save(Reading.of(
                user, criteria, req.readingType(), req.slot(), req.measuredAt(), req.value(), clock.instant()));
        return ReadingResponse.from(saved);
    }

    // 상세
    public ReadingResponse get(Long userId, Long readingId) {
        return ReadingResponse.from(findMine(userId, readingId));
    }

    // 목록 (기간 + 커서). from·to는 한국 날짜, 둘 다 포함. 첫 페이지는 cursorAt·cursorId 없이 부름
    public ReadingPageResponse list(Long userId, LocalDate from, LocalDate to,
                                    Instant cursorAt, Long cursorId, int size) {
        if (from.isAfter(to)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "시작 날짜가 끝 날짜보다 늦어요.");
        }
        if ((cursorAt == null) != (cursorId == null)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT, "cursorAt과 cursorId는 같이 보내 주세요.");
        }
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);   // 0이나 10000이 와도 1~100 사이로 맞춤

        Instant fromAt = startOfKoreanDay(from);
        // 첫 페이지: 커서 = 기간 끝(to 다음 날 0시) → 쿼리의 "커서보다 과거" 조건이 기간 끝 역할도 함
        Instant afterAt = cursorAt != null ? cursorAt : startOfKoreanDay(to.plusDays(1));
        Long afterId = cursorId != null ? cursorId : Long.MAX_VALUE;

        // 하나 더 받아서 다음 페이지가 있는지 확인함 (count 쿼리 안 씀)
        List<Reading> fetched = readingRepository.findPage(
                userId, fromAt, afterAt, afterId, Limit.of(pageSize + 1));
        return ReadingPageResponse.of(fetched, pageSize);
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404 (상세·수정·삭제가 같이 씀)
    private Reading findMine(Long userId, Long readingId) {
        return readingRepository.findByIdAndUserIdAndDeletedAtIsNull(readingId, userId)
                .orElseThrow(() -> new CustomException(ReadingErrorCode.READING_NOT_FOUND));
    }

    // 세계 공통 시각 -> 한국 날짜 (한국 새벽 1시는 UTC로 전날이라서)
    private LocalDate koreanDate(Instant at) {
        return LocalDate.ofInstant(at, clock.getZone());
    }

    // 한국 날짜 -> 그날 0시(한국)의 세계 공통 시각
    private Instant startOfKoreanDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant();
    }
}
