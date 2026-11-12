package com.dangdang.domain.caresite.service;

import com.dangdang.domain.caresite.dto.request.CareSiteCreateRequest;
import com.dangdang.domain.caresite.dto.response.CareSiteResponse;
import com.dangdang.domain.caresite.entity.CareSite;
import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.domain.caresite.repository.CareSiteRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareSiteService {

    private final CareSiteRepository careSiteRepository;
    private final UserRepository userRepository;

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
        return CareSiteResponse.from(saved);
    }

    // 목록: 대표 병원 먼저 → 정렬 순서 → 먼저 등록한 순
    public List<CareSiteResponse> list(Long userId) {
        return careSiteRepository.findAllByUserIdAndDeletedAtIsNullOrderByPrimaryDescSortOrderAscIdAsc(userId)
                .stream()
                .map(CareSiteResponse::from)
                .toList();
    }

    // 지금 대표 병원이 있으면 대표를 풂 (대표는 사용자당 하나만)
    private void releasePrimary(Long userId) {
        careSiteRepository.findByUserIdAndPrimaryTrueAndDeletedAtIsNull(userId)
                .ifPresent(CareSite::unmarkPrimary);
    }

    // 내 것 + 삭제 안 된 것만. 없음·삭제됨·남의 것은 전부 404
    private CareSite findMine(Long userId, Long careSiteId) {
        return careSiteRepository.findByIdAndUserIdAndDeletedAtIsNull(careSiteId, userId)
                .orElseThrow(() -> new CustomException(CareSiteErrorCode.CARE_SITE_NOT_FOUND));
    }
}
