package com.dangdang.domain.shop.service;

import com.dangdang.domain.shop.dto.response.ProductDetailResponse;
import com.dangdang.domain.shop.dto.response.ProductPageResponse;
import com.dangdang.domain.shop.dto.response.ProductSummaryResponse;
import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.enums.ProductCategory;
import com.dangdang.domain.shop.exception.ShopErrorCode;
import com.dangdang.domain.shop.repository.ProductLikeRepository;
import com.dangdang.domain.shop.repository.ProductRepository;
import com.dangdang.domain.shop.repository.ProductViewRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;

// 상점 상품 보기 (P-01 · P-02). 상세를 열면 「최근 본 상품」에도 남긴다. 상품은 운영자가 넣고 여기선 읽기만
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductLikeRepository productLikeRepository;
    private final ProductViewRepository productViewRepository;
    private final Clock clock;

    // category 가 null 이면 전체(「홈」), pick = true 면 산모 PICK 배너만, q 는 이름·브랜드에 들어간 말
    @Transactional(readOnly = true)
    public ProductPageResponse list(Long userId, ProductCategory category, boolean pick, String q, int page, int size) {
        String keyword = q == null ? "" : escapeLike(q.strip());
        Slice<Product> slice = productRepository.search(category == null, category, pick, keyword,
                PageRequest.of(page, size));
        // 하트 칠하기 — 이번 페이지 상품만 한 번에 (빈 페이지면 IN () 이 안 되게 건너뜀)
        Set<Long> liked = slice.isEmpty() ? Set.of()
                : productLikeRepository.findLikedProductIds(userId, slice.map(Product::getId).getContent());
        List<ProductSummaryResponse> items = slice.getContent().stream()
                .map(p -> ProductSummaryResponse.of(p, liked.contains(p.getId())))
                .toList();
        return new ProductPageResponse(items, page, size, slice.hasNext());
    }

    // 상세를 열면 「최근 본 상품」에 남긴다 (같은 상품은 한 줄, 시각만 새로 — 여러 번 불러도 결과가 같음)
    @Transactional
    public ProductDetailResponse detail(Long userId, Long productId) {
        Product product = onSale(productId);
        productViewRepository.upsert(userId, productId, clock.instant());
        return ProductDetailResponse.of(product, productLikeRepository.existsByUserIdAndProductId(userId, productId));
    }

    // 판매 중인 상품. 없거나 내린 상품이면 404 (찜하기·판매처로 이동도 같이 쓴다)
    Product onSale(Long productId) {
        return productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new CustomException(ShopErrorCode.PRODUCT_NOT_FOUND));
    }

    // LIKE 특수 글자(\ % _)를 글자 그대로 찾게. 「70%」를 찾으면 「70」으로 시작하는 모든 것이 아니라 「70%」만
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
