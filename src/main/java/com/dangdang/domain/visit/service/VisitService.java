package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.dto.request.VisitCreateRequest;
import com.dangdang.domain.visit.dto.request.VisitUpdateRequest;
import com.dangdang.domain.visit.dto.response.VisitDetailResponse;
import com.dangdang.domain.visit.dto.response.VisitPageResponse;
import com.dangdang.domain.visit.dto.response.VisitResponse;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.exception.VisitErrorCode;
import com.dangdang.domain.visit.repository.VisitRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitService {

    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final CareSiteRepository careSiteRepository;
    private final Clock clock;

    // 일정 등록. 병원을 고르면 내 병원(삭제 안 된 것)인지 확인
    @Transactional
    public VisitResponse create(Long userId, VisitCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        checkMyCareSite(userId, req.careSiteId());
        Visit visit = visitRepository.saveAndFlush(Visit.of(user, req.careSiteId(), req.visitAt()));
        return VisitResponse.from(visit);
    }

    // 목록: 진료 날짜 이른 순, 날짜 미정은 맨 뒤
    public VisitPageResponse list(Long userId, int page, int size) {
        return VisitPageResponse.from(visitRepository.findPage(userId, PageRequest.of(page, size)));
    }

    // 상세 (질문·메모 포함)
    public VisitDetailResponse get(Long userId, Long visitId) {
        return VisitDetailResponse.from(findMine(userId, visitId));
    }

    // 수정 (보낸 칸만). null을 보내면 병원 지우기·날짜 미정으로
    @Transactional
    public VisitResponse update(Long userId, Long visitId, VisitUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(VisitErrorCode.NOTHING_TO_UPDATE);
        }
        Visit visit = findMine(userId, visitId);
        if (req.isCareSiteIdSent()) {
            checkMyCareSite(userId, req.getCareSiteId());
            visit.changeCareSite(req.getCareSiteId());
        }
        if (req.isVisitAtSent()) {
            visit.changeVisitAt(req.getVisitAt());
        }
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담으려고 먼저 DB에 씀
        visitRepository.flush();
        return VisitResponse.from(visit);
    }

    // 삭제 (soft delete). 이미 지운 일정을 또 지우면 findMine에서 404
    @Transactional
    public void delete(Long userId, Long visitId) {
        findMine(userId, visitId).delete(clock.instant());   // 변경 감지로 트랜잭션 끝날 때 반영됨
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    Visit findMine(Long userId, Long visitId) {
        return visitRepository.findByIdAndUserIdAndDeletedAtIsNull(visitId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.VISIT_NOT_FOUND));
    }

    // 병원을 골랐으면 내 병원(삭제 안 된 것)이어야 함. 없음·삭제됨·남의 병원은 병원 API와 같은 404
    private void checkMyCareSite(Long userId, Long careSiteId) {
        if (careSiteId != null && careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(careSiteId, userId).isEmpty()) {
            throw new CustomException(CareSiteErrorCode.CARE_SITE_NOT_FOUND);
        }
    }
}
