package com.dangdang.domain.food.repository;

import com.dangdang.domain.food.entity.Food;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, String> {

    // 이름 검색. keyword는 띄어쓰기를 뺀 소문자, pattern은 그걸 LIKE용으로 감싼 값 (%·_는 미리 \로 막아 둠)
    // 검색용 이름(search_name, 단어 순서를 바꾼 이름들 포함)이나 대표식품명에 들어 있으면 찾음
    // 순서: 이름이 똑같음(순서 바꾼 이름 포함) → 가정식·외식 → 급식 → 프랜차이즈 → 대표식품명이 똑같음 → 이름 → 식품코드 (페이지가 넘어가도 순서가 안 흔들리게)
    // 이름이 똑같은 것끼리는 조각이 적은 것(재료가 덜 붙은 것) 먼저. "김치볶음밥"이면 볶음밥_김치가 볶음밥_김치_돼지고기보다 위
    // 출처를 대표식품명보다 앞에 둠. 프랜차이즈 피자 수천 개가 대표식품명이 전부 "피자"라서, 반대로 두면 가정식 피자빵이 그 뒤로 밀림
    @Query(nativeQuery = true, value = """
            SELECT * FROM foods f
            WHERE f.search_name LIKE :pattern
               OR lower(replace(f.rep_name, ' ', '')) LIKE :pattern
            ORDER BY
               CASE WHEN position('|' || :keyword || '|' IN '|' || f.search_name || '|') > 0 THEN 0 ELSE 1 END,
               CASE WHEN position('|' || :keyword || '|' IN '|' || f.search_name || '|') > 0
                    THEN cardinality(string_to_array(f.name, '_')) ELSE 0 END,
               CASE WHEN f.origin IN (1, 3, 4) THEN 0
                    WHEN f.origin IN (5, 6, 7) THEN 1
                    ELSE 2 END,
               CASE WHEN lower(replace(f.rep_name, ' ', '')) = :keyword THEN 0 ELSE 1 END,
               f.name, f.food_code
            """,
            countQuery = """
            SELECT count(*) FROM foods f
            WHERE f.search_name LIKE :pattern
               OR lower(replace(f.rep_name, ' ', '')) LIKE :pattern
            """)
    Page<Food> search(@Param("keyword") String keyword, @Param("pattern") String pattern, Pageable pageable);
}
