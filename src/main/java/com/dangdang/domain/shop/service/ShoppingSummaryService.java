package com.dangdang.domain.shop.service;

import com.dangdang.domain.shop.dto.response.ShoppingSummaryResponse;
import com.dangdang.domain.shop.repository.ProductLikeRepository;
import com.dangdang.domain.shop.repository.ProductViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// 마이 › 쇼핑 메뉴 숫자 (M-01 v2). 마이 메인은 들어올 때마다 불리니 목록 대신 숫자 두 개만
// 숫자는 각 목록과 같은 조건 (찜 = 판매 중 · 최근 본 = 오늘 포함 최근 7일(한국 날짜) 안 판매 중)
@Service
@RequiredArgsConstructor
public class ShoppingSummaryService {

    private final ProductLikeRepository productLikeRepository;
    private final ProductViewRepository productViewRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ShoppingSummaryResponse summary(Long userId) {
        return new ShoppingSummaryResponse(
                productLikeRepository.countMine(userId),
                productViewRepository.countRecent(userId, RecentProductService.recentSince(clock.instant())));
    }
}
