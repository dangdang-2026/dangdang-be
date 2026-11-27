package com.dangdang.domain.shop.repository;

import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.enums.ProductCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndActiveTrue(Long id);

    // 판매 중인 상품 전부 (상점 메인 「산모들이 먹고 기록한 간식」·저당 추천 후보, 상품 수가 수십 개라 한 번에 읽는다)
    List<Product> findAllByActiveTrue();

    // 상점 목록 (P-01). 판매 중인 것만, 운영자가 정한 순서대로 (같은 순서면 id)
    //   allCategories = true 면 category 는 안 본다 (「홈」 탭)
    //   keyword 는 "" 이면 전부 (null 을 넘기면 PostgreSQL 이 글자 타입을 몰라서 오류 → 서비스에서 "" 로 바꿔 넘김)
    //   keyword 안의 % _ \ 는 서비스가 \ 를 붙여 넘긴다 → 「글자 그대로」 찾기
    @Query("""
            select p from Product p
            where p.active = true
              and (:allCategories = true or p.category = :category)
              and (:pickOnly = false or p.pick = true)
              and (lower(p.name) like lower(concat('%', :keyword, '%')) escape '\\'
                   or lower(p.brand) like lower(concat('%', :keyword, '%')) escape '\\')
            order by p.sortOrder, p.id
            """)
    Slice<Product> search(@Param("allCategories") boolean allCategories,
                          @Param("category") ProductCategory category,
                          @Param("pickOnly") boolean pickOnly,
                          @Param("keyword") String keyword,
                          Pageable pageable);
}
