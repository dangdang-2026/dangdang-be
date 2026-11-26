package com.dangdang.domain.shop.service;

import com.dangdang.domain.shop.dto.response.ProductSummaryResponse;
import com.dangdang.domain.shop.dto.response.RecentProductListResponse;
import com.dangdang.domain.shop.dto.response.RecentProductResponse;
import com.dangdang.domain.shop.entity.ProductView;
import com.dangdang.domain.shop.exception.ShopErrorCode;
import com.dangdang.domain.shop.repository.ProductLikeRepository;
import com.dangdang.domain.shop.repository.ProductViewRepository;
import com.dangdang.global.common.KoreanTime;
import com.dangdang.global.common.TimeCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// 마이 › 최근 본 상품. 남기는 건 ProductService.detail (상세를 열 때)
@Service
@RequiredArgsConstructor
public class RecentProductService {

    private static final int RECENT_DAYS = 7;   // 「최근 7일」 = 오늘 포함 7일 (한국 날짜)
    private static final String VIEWS = "shop.views";

    private final ProductViewRepository productViewRepository;
    private final ProductLikeRepository productLikeRepository;
    private final Clock clock;

    // 오늘 포함 최근 7일(한국 날짜) · 판매 중인 것만 · 최근 본 것 먼저
    @Transactional(readOnly = true)
    public RecentProductListResponse recent(Long userId, String rawCursor, int size) {
        Instant since = recentSince(clock.instant());
        TimeCursor cursor = rawCursor == null ? null : TimeCursor.decode(rawCursor, VIEWS, ShopErrorCode.INVALID_CURSOR);
        List<ProductView> found = cursor == null
                ? productViewRepository.findRecent(userId, since, Limit.of(size + 1))
                : productViewRepository.findRecentBefore(userId, since, cursor.at(), cursor.id(), Limit.of(size + 1));
        boolean hasNext = found.size() > size;
        List<ProductView> page = hasNext ? found.subList(0, size) : found;
        String next = null;
        if (hasNext) {
            ProductView last = page.get(page.size() - 1);
            next = new TimeCursor(last.getViewedAt(), last.getId()).encode(VIEWS);
        }
        Set<Long> liked = page.isEmpty() ? Set.of()
                : productLikeRepository.findLikedProductIds(userId, page.stream().map(v -> v.getProduct().getId()).toList());
        List<RecentProductResponse> items = page.stream()
                .map(v -> new RecentProductResponse(
                        ProductSummaryResponse.of(v.getProduct(), liked.contains(v.getProduct().getId())), v.getViewedAt()))
                .toList();
        return new RecentProductListResponse(items, next, hasNext);
    }

    // 「최근 7일」이 시작하는 시각 = 오늘(한국) 포함 7일 → 6일 전 한국 00:00. 마이 쇼핑 숫자도 이 기준
    static Instant recentSince(Instant now) {
        return KoreanTime.startOfDay(KoreanTime.dateOf(now).minusDays(RECENT_DAYS - 1));
    }

    // 하나 지우기 (본 적 없어도 200)
    @Transactional
    public void remove(Long userId, Long productId) {
        productViewRepository.remove(userId, productId);
    }

    // 모두 지우기 (7일 지난 것·판매 끝난 것까지 전부). 화면에 안 보이던 것도 지워져서 개수는 안 돌려줌
    @Transactional
    public void clear(Long userId) {
        productViewRepository.removeAll(userId);
    }
}
