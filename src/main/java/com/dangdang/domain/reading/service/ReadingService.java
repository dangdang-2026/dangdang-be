package com.dangdang.domain.reading.service;

import com.dangdang.domain.criteria.entity.CriteriaVersion;
import com.dangdang.domain.criteria.service.CriteriaService;
import com.dangdang.domain.reading.dto.request.ReadingCreateRequest;
import com.dangdang.domain.reading.dto.response.ReadingResponse;
import com.dangdang.domain.reading.entity.Reading;
import com.dangdang.domain.reading.repository.ReadingRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingService {

    private final ReadingRepository readingRepository;
    private final UserRepository userRepository;
    private final CriteriaService criteriaService;
    private final Clock clock;

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

    // 세계 공통 시각 -> 한국 날짜 (한국 새벽 1시는 UTC로 전날이라서)
    private LocalDate koreanDate(Instant at) {
        return LocalDate.ofInstant(at, clock.getZone());
    }
}
