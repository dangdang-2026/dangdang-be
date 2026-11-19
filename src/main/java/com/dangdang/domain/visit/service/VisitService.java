package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.visit.dto.request.VisitCreateRequest;
import com.dangdang.domain.visit.dto.request.VisitUpdateRequest;
import com.dangdang.domain.visit.dto.response.VisitDetailResponse;
import com.dangdang.domain.visit.dto.response.VisitNoteResponse;
import com.dangdang.domain.visit.dto.response.VisitPageResponse;
import com.dangdang.domain.visit.dto.response.VisitQuestionResponse;
import com.dangdang.domain.visit.dto.response.VisitResponse;
import com.dangdang.domain.visit.entity.Visit;
import com.dangdang.domain.visit.exception.VisitErrorCode;
import com.dangdang.domain.visit.repository.VisitNoteRepository;
import com.dangdang.domain.visit.repository.VisitQuestionRepository;
import com.dangdang.domain.visit.repository.VisitRepository;
import com.dangdang.global.config.ClockConfig;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitService {

    private final VisitRepository visitRepository;
    private final UserRepository userRepository;
    private final CareSiteRepository careSiteRepository;
    private final VisitQuestionRepository visitQuestionRepository;
    private final VisitNoteRepository visitNoteRepository;
    private final Clock clock;

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    // 일정 등록. 병원을 고르면 내 병원(삭제 안 된 것)인지 확인
    @Transactional
    public VisitResponse create(Long userId, VisitCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        checkMyCareSite(userId, req.careSiteId());
        checkVisitAt(req.visitAt());
        Visit visit = visitRepository.saveAndFlush(Visit.of(user, req.careSiteId(), req.visitAt()));
        return VisitResponse.from(visit);
    }

    // 목록: 진료 날짜 이른 순, 날짜 미정은 맨 뒤
    public VisitPageResponse list(Long userId, int page, int size) {
        return VisitPageResponse.from(visitRepository.findPage(userId, PageRequest.of(page, size)));
    }

    // 상세 (질문·메모 포함). 질문은 정렬 순서 → 먼저 만든 순, 메모는 먼저 쓴 순
    // 질문·메모 서비스가 이 서비스(findMine)를 쓰므로, 서로 부르지 않게 저장소에서 바로 읽음
    public VisitDetailResponse get(Long userId, Long visitId) {
        Visit visit = findMine(userId, visitId);
        List<VisitQuestionResponse> questions = visitQuestionRepository
                .findAllByVisitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(visitId).stream()
                .map(VisitQuestionResponse::from)
                .toList();
        List<VisitNoteResponse> notes = visitNoteRepository
                .findAllByVisitIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(visitId).stream()
                .map(VisitNoteResponse::from)
                .toList();
        return VisitDetailResponse.of(visit, questions, notes);
    }

    // 수정 (보낸 칸만). null을 보내면 병원 지우기·날짜 미정으로
    @Transactional
    public VisitResponse update(Long userId, Long visitId, VisitUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(VisitErrorCode.NOTHING_TO_UPDATE);
        }
        Visit visit = findMine(userId, visitId);
        // 병원은 실제로 바꿀 때만 확인함. 화면이 원래 병원 id를 그대로 다시 보냈는데
        // 그 병원이 그사이 지워졌다고 수정 전체가 404로 실패하면 안 돼서
        if (req.isCareSiteIdSent() && !Objects.equals(req.getCareSiteId(), visit.getCareSiteId())) {
            checkMyCareSite(userId, req.getCareSiteId());
            visit.changeCareSite(req.getCareSiteId());
        }
        if (req.isVisitAtSent()) {
            checkVisitAt(req.getVisitAt());
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

    // 진료 날짜는 한국 기준 2000~2100년만 받음 (다른 날짜 입력 QueryParams와 같은 범위)
    // "+300000-01-01T00:00:00Z" 같은 값도 Instant로 읽혀서, 그대로 두면 DB(TIMESTAMPTZ)에 못 넣어 500이 남
    private void checkVisitAt(Instant visitAt) {
        if (visitAt == null) {
            return;
        }
        int year = visitAt.atZone(ClockConfig.KST).getYear();
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT,
                    "진료 날짜는 " + MIN_YEAR + "~" + MAX_YEAR + "년 사이로 보내 주세요.");
        }
    }

    // 병원을 골랐으면 내 병원(삭제 안 된 것)이어야 함. 없음·삭제됨·남의 병원은 병원 API와 같은 404
    private void checkMyCareSite(Long userId, Long careSiteId) {
        if (careSiteId != null && careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(careSiteId, userId).isEmpty()) {
            throw new CustomException(CareSiteErrorCode.CARE_SITE_NOT_FOUND);
        }
    }
}
