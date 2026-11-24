package com.dangdang.domain.postpartum.service;

import com.dangdang.domain.postpartum.dto.request.PostpartumTestCreateRequest;
import com.dangdang.domain.postpartum.dto.request.PostpartumTestUpdateRequest;
import com.dangdang.domain.postpartum.dto.response.PostpartumTestResponse;
import com.dangdang.domain.postpartum.entity.PostpartumTest;
import com.dangdang.domain.postpartum.exception.PostpartumErrorCode;
import com.dangdang.domain.postpartum.repository.PostpartumTestRepository;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.pregnancy.repository.PregnancyRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

// 산후 검사 일정 (PP-04, PP-05). 이름과 날짜는 병원에서 들은 그대로 — 앱이 시기를 계산하지 않는다
@Service
@RequiredArgsConstructor
public class PostpartumTestService {

    private final PostpartumTestRepository postpartumTestRepository;
    private final PregnancyRepository pregnancyRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    @Transactional
    public PostpartumTestResponse create(Long userId, PostpartumTestCreateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        var pregnancy = pregnancyRepository.findByUserIdAndStateNot(userId, PregnancyState.ENDED)
                .filter(p -> p.getState() == PregnancyState.POSTPARTUM)
                .orElseThrow(() -> new CustomException(PostpartumErrorCode.NOT_POSTPARTUM));
        PostpartumTest saved = postpartumTestRepository.save(PostpartumTest.of(pregnancy.getUser(), req.name().strip(),
                req.testDate(), req.remindDayBefore() == null || req.remindDayBefore(),
                req.remindSameDay() == null || req.remindSameDay(), clock.instant()));
        return PostpartumTestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PostpartumTestResponse> list(Long userId) {
        return postpartumTestRepository.findByUserIdOrderByTestDateAscIdAsc(userId).stream()
                .map(PostpartumTestResponse::from).toList();
    }

    @Transactional
    public PostpartumTestResponse update(Long userId, Long testId, PostpartumTestUpdateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        PostpartumTest test = mine(userId, testId);
        test.change(req.name() == null ? null : req.name().strip(), req.testDate(), req.remindDayBefore(),
                req.remindSameDay(), clock.instant());
        return PostpartumTestResponse.from(test);
    }

    // E-11 결과 메모 — 빈 글은 지움(null)
    @Transactional
    public PostpartumTestResponse writeResult(Long userId, Long testId, String note) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        PostpartumTest test = mine(userId, testId);
        test.writeResult(note == null || note.isBlank() ? null : note.strip(), clock.instant());
        return PostpartumTestResponse.from(test);
    }

    @Transactional
    public void delete(Long userId, Long testId) {
        postpartumTestRepository.delete(mine(userId, testId));
    }

    private PostpartumTest mine(Long userId, Long testId) {
        return postpartumTestRepository.findByIdAndUserId(testId, userId)
                .orElseThrow(() -> new CustomException(PostpartumErrorCode.TEST_NOT_FOUND));
    }
}
