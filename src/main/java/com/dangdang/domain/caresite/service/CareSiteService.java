package com.dangdang.domain.caresite.service;

import com.dangdang.domain.caresite.dto.request.CareSiteCreateRequest;
import com.dangdang.domain.caresite.dto.request.CareSiteUpdateRequest;
import com.dangdang.domain.caresite.dto.response.CareSiteResponse;
import com.dangdang.domain.caresite.dto.response.HospitalSearchResponse;
import com.dangdang.domain.caresite.infra.HospitalSearchClient;
import com.dangdang.domain.caresite.infra.HospitalSearchResult;
import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.caresite.repository.HospitalRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.domain.visit.repository.VisitNoteRepository;
import com.dangdang.domain.visit.repository.VisitQuestionRepository;
import com.dangdang.domain.visit.repository.VisitRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareSiteService {

    private final CareSiteRepository careSiteRepository;
    private final UserRepository userRepository;
    private final HospitalSearchClient hospitalSearchClient;
    private final HospitalRepository hospitalRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;
    private final VisitRepository visitRepository;
    private final VisitQuestionRepository visitQuestionRepository;
    private final VisitNoteRepository visitNoteRepository;

    private static final int MIN_KEYWORD = 2;        // 한 글자면 결과가 너무 많음
    private static final int MAX_KEYWORD = 50;       // 병원 이름 칸 길이와 같음
    private static final int MAX_SEARCH_SIZE = 50;

    // 병원 등록. 대표로 등록하면 원래 대표 병원은 풀림
    @Transactional
    public CareSiteResponse create(Long userId, CareSiteCreateRequest req) {
        // 대표로 등록하면 회원 줄을 잠가서 같은 사용자의 대표 지정 요청이 한 번에 하나씩 처리되게 함
        User user = (req.isPrimary() ? userRepository.findByIdForUpdate(userId) : userRepository.findById(userId))
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403 (대표면 위에서 이미 더 센 잠금을 잡음)
        if (req.isPrimary()) {
            releasePrimary(userId);
        }
        // 정렬 순서를 안 보내면 맨 뒤 (지금 병원 개수)
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : list(userId).size();
        CareSite careSite = CareSite.of(user, req.name(), req.department(), req.hiraCode(), req.address(),
                req.phone(), req.roleNote(), req.isPrimary(), sortOrder);
        try {
            // id를 DB가 만들어서(IDENTITY) save 순간 바로 INSERT가 나감 -> 대표 하나 인덱스 충돌도 여기서 남
            careSiteRepository.saveAndFlush(careSite);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CareSiteErrorCode.PRIMARY_CONFLICT);
        }
        return CareSiteResponse.from(careSite);
    }

    // 목록: 대표 병원 먼저 → 정렬 순서 → 먼저 등록한 순
    public List<CareSiteResponse> list(Long userId) {
        return careSiteRepository.findAllByUserIdAndDeletedAtIsNullOrderByPrimaryDescSortOrderAscIdAsc(userId)
                .stream()
                .map(CareSiteResponse::from)
                .toList();
    }

    // 수정 (보낸 칸만). primary=true면 원래 대표를 풀고 이 병원을 대표로, false면 대표 해제
    @Transactional
    public CareSiteResponse update(Long userId, Long careSiteId, CareSiteUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(CareSiteErrorCode.NOTHING_TO_UPDATE);
        }
        // 대표로 지정하면 등록과 같이 회원 줄부터 잠금 (병원을 읽기 전에 잠가야 최신 대표 상태를 봄)
        if (Boolean.TRUE.equals(req.primary())) {
            userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        }
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403 (병원 줄을 읽기 전에)
        CareSite careSite = findMine(userId, careSiteId);
        careSite.update(req.name(), req.department(), req.hiraCode(), req.address(), req.phone(),
                req.roleNote(), req.sortOrder());
        if (Boolean.TRUE.equals(req.primary()) && !careSite.isPrimary()) {
            releasePrimary(userId);
            careSite.markPrimary();
        } else if (Boolean.FALSE.equals(req.primary())) {
            careSite.unmarkPrimary();
        }
        // 변경 감지로 트랜잭션 끝날 때 반영되지만, 응답에 새 updatedAt을 담고 대표 충돌도 여기서 잡으려고 먼저 DB에 씀
        flushPrimary();
        return CareSiteResponse.from(careSite);
    }

    // 삭제 (soft delete). 이미 지운 병원을 또 지우면 findMine에서 404
    // 이 병원을 가리키던 병원 기준(criteria_versions)·지난 일정·리포트·식사 안내는 그대로 둠 (지난 기록이 그때 병원을 가리켜야 해서)
    // 아직 안 온 일정·날짜 미정 일정은 질문·메모까지 같이 지운다 (C-20 「함께 지워져요: 다음 진료일 · 진료 알림」)
    // 진료 알림(VISIT_BEFORE·VISIT_TODAY)은 서버가 아직 만들지 않아서 따로 취소할 예약이 없다
    @Transactional
    public void delete(Long userId, Long careSiteId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);   // DB(TIMESTAMPTZ)와 같은 정밀도 (일정 삭제와 같게)
        findMine(userId, careSiteId).delete(now);   // 아래 일괄 수정이 먼저 DB에 써 준다 (flushAutomatically)
        List<Long> visitIds = visitRepository.findUpcomingIdsAtCareSite(userId, careSiteId, now);
        if (!visitIds.isEmpty()) {
            visitQuestionRepository.deleteAllOfVisits(visitIds, now);
            visitNoteRepository.deleteAllOfVisits(visitIds, now);
            visitRepository.deleteAllByIds(visitIds, now);
        }
        log.info("[CareSite] 병원 삭제 - 사용자: {}, 같이 지운 다가오는 일정: {}개", userId, visitIds.size());
    }

    // 병원 검색. page는 0부터, size는 1~50으로 맞춤
    // 끝까지 받은 병원 목록(hospitals)이 있으면 DB에서 찾고, 아직 없으면(첫 적재 전·도중·중간에 멈춤) 예전처럼 심평원에 물어봄
    public HospitalSearchResponse search(String keyword, int page, int size) {
        String trimmed = keyword == null ? "" : keyword.strip();
        if (trimmed.length() < MIN_KEYWORD || trimmed.length() > MAX_KEYWORD) {
            throw new CustomException(CareSiteErrorCode.INVALID_KEYWORD);
        }
        int pageNo = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, MAX_SEARCH_SIZE);
        HospitalSearchResult result = hospitalRepository.hasCompletedSync()
                ? hospitalRepository.search(HospitalRepository.toSearchName(trimmed), (long) pageNo * pageSize, pageSize)
                : hospitalSearchClient.search(trimmed, pageNo + 1, pageSize);   // 심평원은 1부터
        return HospitalSearchResponse.of(result, pageNo, pageSize);
    }

    // 지금 대표 병원이 있으면 대표를 풂 (대표는 사용자당 하나만)
    // 바로 DB에 씀: 안 그러면 새 대표 INSERT·UPDATE가 옛 대표 해제보다 먼저 나가서 대표 하나 인덱스에 걸림
    private void releasePrimary(Long userId) {
        careSiteRepository.findByUserIdAndPrimaryTrueAndDeletedAtIsNull(userId)
                .ifPresent(CareSite::unmarkPrimary);
        careSiteRepository.flush();
    }

    // DB에 씀. 회원 줄 잠금이 있어서 보통은 안 걸리지만, 잠금을 빠뜨린 경로가 생겨 대표 하나 인덱스(ux_care_sites_primary)에 걸리면 409
    private void flushPrimary() {
        try {
            careSiteRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CareSiteErrorCode.PRIMARY_CONFLICT);
        }
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    private CareSite findMine(Long userId, Long careSiteId) {
        return careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(careSiteId, userId)
                .orElseThrow(() -> new CustomException(CareSiteErrorCode.CARE_SITE_NOT_FOUND));
    }
}
