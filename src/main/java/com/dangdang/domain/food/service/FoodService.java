package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodPageResponse;
import com.dangdang.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// 음식 (식약처 음식 영양성분 데이터) 조회
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodService {

    private final FoodRepository foodRepository;

    // 이름 검색. 띄어쓰기는 무시함 ("김치 볶음밥" = "김치볶음밥")
    public FoodPageResponse search(String q, int page, int size) {
        String keyword = q.replace(" ", "").toLowerCase(Locale.ROOT);
        String pattern = "%" + escapeLike(keyword) + "%";
        return FoodPageResponse.from(foodRepository.search(keyword, pattern, PageRequest.of(page, size)));
    }

    // 검색어에 든 % _ \ 를 글자 그대로 찾게 막음 (PostgreSQL LIKE 기본 이스케이프 문자가 \)
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
