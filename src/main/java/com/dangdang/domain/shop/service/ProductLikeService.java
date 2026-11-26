package com.dangdang.domain.shop.service;

import com.dangdang.domain.shop.dto.response.LikedProductListResponse;
import com.dangdang.domain.shop.dto.response.ProductSummaryResponse;
import com.dangdang.domain.shop.entity.ProductLike;
import com.dangdang.domain.shop.exception.ShopErrorCode;
import com.dangdang.domain.shop.repository.ProductLikeRepository;
import com.dangdang.global.common.TimeCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

// 찜 (하트 · M-21 찜한 상품)
@Service
@RequiredArgsConstructor
public class ProductLikeService {

    private static final String LIKES = "shop.likes";   // 커서를 만든 목록 이름 (다른 목록 커서 막기)

    private final ProductService productService;
    private final ProductLikeRepository productLikeRepository;
    private final Clock clock;

    // 찜하기. 이미 찜했어도 200 (두 번 눌러도, 동시에 눌러도 한 줄)
    @Transactional
    public void like(Long userId, Long productId) {
        productService.onSale(productId);
        productLikeRepository.addIfAbsent(userId, productId, clock.instant());
    }

    // 찜 빼기. 찜 안 했거나 판매가 끝난 상품이어도 200 (빼는 건 막을 이유가 없음)
    @Transactional
    public void unlike(Long userId, Long productId) {
        productLikeRepository.remove(userId, productId);
    }

    // 최근에 찜한 것 먼저, 판매 끝난 상품 빼고. totalCount 도 같은 조건
    @Transactional(readOnly = true)
    public LikedProductListResponse liked(Long userId, String rawCursor, int size) {
        TimeCursor cursor = rawCursor == null ? null : TimeCursor.decode(rawCursor, LIKES, ShopErrorCode.INVALID_CURSOR);
        List<ProductLike> found = cursor == null
                ? productLikeRepository.findMine(userId, Limit.of(size + 1))
                : productLikeRepository.findMineBefore(userId, cursor.at(), cursor.id(), Limit.of(size + 1));
        boolean hasNext = found.size() > size;
        List<ProductLike> page = hasNext ? found.subList(0, size) : found;
        String next = null;
        if (hasNext) {
            ProductLike last = page.get(page.size() - 1);
            next = new TimeCursor(last.getCreatedAt(), last.getId()).encode(LIKES);
        }
        List<ProductSummaryResponse> items = page.stream()
                .map(l -> ProductSummaryResponse.of(l.getProduct(), true))
                .toList();
        return new LikedProductListResponse(items, next, hasNext, productLikeRepository.countMine(userId));
    }
}
