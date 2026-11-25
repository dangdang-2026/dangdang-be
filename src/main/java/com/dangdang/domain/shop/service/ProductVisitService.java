package com.dangdang.domain.shop.service;

import com.dangdang.domain.shop.dto.response.ProductOutboundResponse;
import com.dangdang.domain.shop.dto.response.ProductVisitListResponse;
import com.dangdang.domain.shop.dto.response.ProductVisitResponse;
import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.entity.ProductVisit;
import com.dangdang.domain.shop.exception.ShopErrorCode;
import com.dangdang.domain.shop.repository.ProductVisitRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.common.TimeCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// 판매처로 이동 (P-06) + 판매처로 간 기록 (M-22)
// 판매처 주소는 DB에 있는 것만 돌려준다 → 사용자가 보낸 주소로 보내는 일(open redirect)이 없음
@Service
@RequiredArgsConstructor
public class ProductVisitService {

    private static final String VISITS = "shop.visits";
    private static final Duration MERGE_WITHIN = Duration.ofMinutes(10);   // 10분 안에 같은 상품을 또 누르면 한 줄로

    private final ProductService productService;
    private final ProductVisitRepository productVisitRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public ProductOutboundResponse visit(Long userId, Long productId) {
        Product product = productService.onSale(productId);
        productVisitRepository.lockUser(userId);   // 더블탭 동시 요청도 한 줄로 (잠근 뒤에 「10분 안 기록」을 읽는다)
        Instant now = clock.instant();
        productVisitRepository
                .findFirstByUserIdAndProductIdAndVisitedAtAfterOrderByVisitedAtDescIdDesc(
                        userId, productId, now.minus(MERGE_WITHIN))
                .ifPresentOrElse(
                        v -> v.revisit(product, now),
                        () -> productVisitRepository.save(
                                ProductVisit.of(userRepository.getReferenceById(userId), product, now)));
        return new ProductOutboundResponse(product.getSellerUrl());
    }

    // 최근 것 먼저. 판매 끝난 상품도 남기고 available=false
    @Transactional(readOnly = true)
    public ProductVisitListResponse list(Long userId, String rawCursor, int size) {
        TimeCursor cursor = rawCursor == null ? null : TimeCursor.decode(rawCursor, VISITS, ShopErrorCode.INVALID_CURSOR);
        List<ProductVisit> found = cursor == null
                ? productVisitRepository.findMine(userId, Limit.of(size + 1))
                : productVisitRepository.findMineBefore(userId, cursor.at(), cursor.id(), Limit.of(size + 1));
        boolean hasNext = found.size() > size;
        List<ProductVisit> page = hasNext ? found.subList(0, size) : found;
        String next = null;
        if (hasNext) {
            ProductVisit last = page.get(page.size() - 1);
            next = new TimeCursor(last.getVisitedAt(), last.getId()).encode(VISITS);
        }
        return new ProductVisitListResponse(page.stream().map(ProductVisitResponse::from).toList(), next, hasNext);
    }

    // 「기록 모두 지우기」. 지운 개수를 돌려준다 (없어도 200, 0)
    // visit 과 같은 사람 잠금을 먼저 — 「다시 가기」가 읽어 둔 줄을 그사이 지워서 revisit 이 500(StaleState) 나지 않게
    @Transactional
    public int clear(Long userId) {
        productVisitRepository.lockUser(userId);
        return productVisitRepository.removeAll(userId);
    }
}
