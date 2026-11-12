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
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareSiteService {

    private final CareSiteRepository careSiteRepository;
    private final UserRepository userRepository;
    private final HospitalSearchClient hospitalSearchClient;
    private final Clock clock;

    private static final int MIN_KEYWORD = 2;        // 한 글자면 결과가 너무 많음
    private static final int MAX_KEYWORD = 50;       // 병원 이름 칸 길이와 같음
    private static final int MAX_SEARCH_SIZE = 50;

    // 병원 등록. 대표로 등록하면 원래 대표 병원은 풀림
    @Transactional
    public CareSiteResponse create(Long userId, CareSiteCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (req.isPrimary()) {
            releasePrimary(userId);
        }
        // 정렬 순서를 안 보내면 맨 뒤 (지금 병원 개수)
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : list(userId).size();
        CareSite saved = careSiteRepository.save(CareSite.of(
                user, req.name(), req.department(), req.hiraCode(), req.address(), req.phone(),
                req.roleNote(), req.isPrimary(), sortOrder));
        flushPrimary();
        return CareSiteResponse.from(saved);
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
    // 이 병원을 가리키던 병원 기준(criteria_versions)은 그대로 둠 (지난 기록이 그때 병원을 가리켜야 해서)
    @Transactional
    public void delete(Long userId, Long careSiteId) {
        findMine(userId, careSiteId).delete(clock.instant());   // 변경 감지로 트랜잭션 끝날 때 반영됨
    }

    // 병원 검색 (심평원). DB는 안 건드림. page는 0부터, size는 1~50으로 맞춤
    public HospitalSearchResponse search(String keyword, int page, int size) {
        String trimmed = keyword == null ? "" : keyword.strip();
        if (trimmed.length() < MIN_KEYWORD || trimmed.length() > MAX_KEYWORD) {
            throw new CustomException(CareSiteErrorCode.INVALID_KEYWORD);
        }
        int pageNo = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, MAX_SEARCH_SIZE);
        HospitalSearchResult result = hospitalSearchClient.search(trimmed, pageNo + 1, pageSize);   // 심평원은 1부터
        return HospitalSearchResponse.of(result, pageNo, pageSize);
    }

    // 지금 대표 병원이 있으면 대표를 풂 (대표는 사용자당 하나만)
    // 바로 DB에 씀: 안 그러면 새 대표 INSERT·UPDATE가 옛 대표 해제보다 먼저 나가서 대표 하나 인덱스에 걸림
    private void releasePrimary(Long userId) {
        careSiteRepository.findByUserIdAndPrimaryTrueAndDeletedAtIsNull(userId)
                .ifPresent(CareSite::unmarkPrimary);
        careSiteRepository.flush();
    }

    // DB에 씀. 대표 지정이 동시에 와서 대표 하나 인덱스(ux_care_sites_primary)에 걸리면 409
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
