package com.dangdang.domain.visit.service;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
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
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.common.QueryParams;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final EntityManager entityManager;

    // 진료 날짜로 받을 수 있는 범위: 한국 기준 2000-01-01 0시 이상 ~ 2101-01-01 0시 미만 (QueryParams와 같은 연도)
    private static final Instant MIN_VISIT_AT = KoreanTime.startOfDay(LocalDate.of(QueryParams.MIN_YEAR, 1, 1));
    private static final Instant MAX_VISIT_AT = KoreanTime.startOfDay(LocalDate.of(QueryParams.MAX_YEAR + 1, 1, 1));

    // 일정 등록. 병원을 고르면 내 병원(삭제 안 된 것)인지 확인
    @Transactional
    public VisitResponse create(Long userId, VisitCreateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
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
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        if (req.isEmpty()) {
            throw new CustomException(VisitErrorCode.NOTHING_TO_UPDATE);
        }
        Visit visit = findMine(userId, visitId);
        // 병원은 실제로 바꿀 때만 확인함. 화면이 원래 병원 id를 그대로 다시 보냈는데
        // 그 병원이 그사이 지워졌다고 수정 전체가 404로 실패하면 안 돼서
        boolean changesCareSite = req.isCareSiteIdSent() && !Objects.equals(req.getCareSiteId(), visit.getCareSiteId());
        if (changesCareSite) {
            checkMyCareSite(userId, req.getCareSiteId());
        } else if (visit.getCareSiteId() != null && req.isVisitAtSent()
                && becomesUpcoming(visit.getVisitAt(), req.getVisitAt())) {
            // 지난 일정을 다가오는 일정(또는 날짜 미정)으로 옮길 때도 병원을 본다
            // 지운 병원의 다가오는 일정은 병원 지우기 때 같이 지워지므로(C-20), 지운 병원으로는 미래로 못 옮긴다
            checkMyCareSite(userId, visit.getCareSiteId());
        }
        // 병원 줄을 잡은 뒤, 값을 바꾸기 전에 일정 줄을 FOR UPDATE로 잠그고 다시 본다 (잠금 순서 care_sites → visits)
        // 위에서 읽은 일정은 잠그지 않은 값이라, 그사이 병원 지우기가 이 일정을 지웠으면 여기서 404 (지운 일정을 되살리지 않게)
        lockMine(userId, visitId);
        // 이미 읽어 둔 엔티티는 잠금 조회로 값이 다시 채워지지 않아서, 잠근 줄의 최신 값(진료 날짜 등)으로 새로 읽음
        // → 「한국 날짜가 바뀌었나」를 다른 기기가 막 바꾼 날짜 기준으로 판단 (아직 바꾼 값이 없어서 버릴 것도 없음)
        entityManager.refresh(visit);
        if (changesCareSite) {
            visit.changeCareSite(req.getCareSiteId());
        }
        if (req.isVisitAtSent()) {
            checkVisitAt(req.getVisitAt());
            if (visit.changeVisitAt(req.getVisitAt())) {
                // 한국 날짜가 바뀌면 진료 알림 보낸 기록을 DB에서 직접 비움 (일정 줄은 위에서 잠갔음)
                // 처음 읽은 일정은 잠그기 전 값이라 보낸 시각이 옛 값(null)일 수 있어서, 엔티티 값만 바꾸면 안 쓰일 수 있음
                visitRepository.clearReminderSent(visitId);
            }
        }
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담으려고 먼저 DB에 씀
        visitRepository.flush();
        return VisitResponse.from(visit);
    }

    // 삭제 (soft delete). 이미 지운 일정을 또 지우면 findMine에서 404
    // 그 일정의 질문·메모도 같은 시각으로 같이 지움 (나중에 질문·메모를 직접 읽는 기능에서 섞이지 않게)
    // 일정을 먼저 지움 표시 → 질문·메모 일괄 수정이 그 변경을 먼저 DB에 써 주고(flushAutomatically) 실행됨
    @Transactional
    public void delete(Long userId, Long visitId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);   // DB(TIMESTAMPTZ)와 같은 정밀도
        // 줄을 잠그고 읽음 — 진료 알림이 이 일정을 처리하는 중이면 끝날 때까지 기다린 뒤 지움 (수정과 같은 순서)
        visitRepository.findMineForUpdate(visitId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.VISIT_NOT_FOUND))
                .delete(now);
        visitQuestionRepository.deleteAllOfVisit(visitId, now);
        visitNoteRepository.deleteAllOfVisit(visitId, now);
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    Visit findMine(Long userId, Long visitId) {
        return visitRepository.findByIdAndUserIdAndDeletedAtIsNull(visitId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.VISIT_NOT_FOUND));
    }

    // findMine과 같은데 일정 줄을 FOR UPDATE로 잠근다 (일정 수정)
    private Visit lockMine(Long userId, Long visitId) {
        return visitRepository.findMineForUpdate(visitId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.VISIT_NOT_FOUND));
    }

    // findMine과 같은데 일정 줄을 FOR SHARE로 잠근다 (질문·메모 추가)
    // 일정·병원 지우기와 겹치면 지우기가 끝난 뒤 404, 추가가 먼저면 지우기가 새 질문·메모까지 지운다
    Visit findMineForShare(Long userId, Long visitId) {
        return visitRepository.findMineForShare(visitId, userId)
                .orElseThrow(() -> new CustomException(VisitErrorCode.VISIT_NOT_FOUND));
    }

    // 진료 날짜는 한국 기준 2000~2100년만 받음 (다른 날짜 입력 QueryParams와 같은 범위)
    // "+300000-01-01T00:00:00Z" 같은 값도 Instant로 읽혀서, 그대로 두면 DB(TIMESTAMPTZ)에 못 넣어 500이 남
    // 연도를 꺼내려고 시간대를 바꾸면 Instant 끝에 가까운 값에서 그 변환이 먼저 터지므로, 경계 시각과 바로 비교함
    private void checkVisitAt(Instant visitAt) {
        if (visitAt == null) {
            return;
        }
        if (visitAt.isBefore(MIN_VISIT_AT) || !visitAt.isBefore(MAX_VISIT_AT)) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT,
                    "진료 날짜는 " + QueryParams.MIN_YEAR + "~" + QueryParams.MAX_YEAR + "년 사이로 보내 주세요.");
        }
    }

    // 병원을 골랐으면 내 병원(삭제 안 된 것)이어야 함. 없음·삭제됨·남의 병원은 병원 API와 같은 404
    // 병원 줄을 FOR SHARE로 잠그고 본다 → 병원 지우기(FOR UPDATE)와 겹치면 지우기가 끝난 뒤 지워진 상태를 보고 404
    // (안 잠그면 지우는 사이 새로 넣은 다가오는 일정이 지운 병원에 남는다). 잠금 순서는 care_sites → visits
    private void checkMyCareSite(Long userId, Long careSiteId) {
        if (careSiteId == null) {
            return;
        }
        boolean alive = careSiteRepository.findMineForShare(careSiteId, userId)
                .map(site -> site.getDeletedAt() == null)
                .orElse(false);
        if (!alive) {
            throw new CustomException(CareSiteErrorCode.CARE_SITE_NOT_FOUND);
        }
    }

    // 지난 일정이 다가오는 일정(지금 이후 또는 날짜 미정)이 되는지. 병원 지우기가 지우는 범위와 같은 기준
    private boolean becomesUpcoming(Instant before, Instant after) {
        Instant now = clock.instant();
        return !isUpcoming(before, now) && isUpcoming(after, now);
    }

    private static boolean isUpcoming(Instant visitAt, Instant now) {
        return visitAt == null || !visitAt.isBefore(now);
    }
}
