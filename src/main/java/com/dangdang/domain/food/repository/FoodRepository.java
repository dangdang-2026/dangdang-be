package com.dangdang.domain.food.repository;

import com.dangdang.domain.food.entity.Food;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, String> {

    // 이름 검색. keyword는 띄어쓰기를 뺀 소문자, pattern은 그걸 LIKE용으로 감싼 값 (%·_는 미리 \로 막아 둠)
    // 식품명이나 대표식품명에 들어 있으면 찾고, 비교할 때 양쪽 다 띄어쓰기를 뺌
    // 순서: 식품명이 똑같음 → 대표식품명이 똑같음 → 가정식·외식 → 급식 → 프랜차이즈 → 이름 → 식품코드 (페이지가 넘어가도 순서가 안 흔들리게)
    @Query(nativeQuery = true, value = """
            SELECT * FROM foods f
            WHERE lower(replace(f.name, ' ', '')) LIKE :pattern
               OR lower(replace(f.rep_name, ' ', '')) LIKE :pattern
            ORDER BY
               CASE WHEN lower(replace(f.name, ' ', '')) = :keyword THEN 0
                    WHEN lower(replace(f.rep_name, ' ', '')) = :keyword THEN 1
                    ELSE 2 END,
               CASE WHEN f.origin IN (1, 3, 4) THEN 0
                    WHEN f.origin IN (5, 6, 7) THEN 1
                    ELSE 2 END,
               f.name, f.food_code
            """,
            countQuery = """
            SELECT count(*) FROM foods f
            WHERE lower(replace(f.name, ' ', '')) LIKE :pattern
               OR lower(replace(f.rep_name, ' ', '')) LIKE :pattern
            """)
    Page<Food> search(@Param("keyword") String keyword, @Param("pattern") String pattern, Pageable pageable);
}
