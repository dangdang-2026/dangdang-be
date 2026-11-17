package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodPageResponse;
import com.dangdang.domain.food.dto.response.FoodResponse;
import com.dangdang.domain.food.exception.FoodErrorCode;
import com.dangdang.domain.food.repository.FoodRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

// 음식 (식약처 음식 영양성분 데이터) 조회
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodService {

    private final FoodRepository foodRepository;

    // 이름 검색. 띄어쓰기는 무시함 ("김치 볶음밥" = "김치볶음밥")
    // 다른 앱에서 복사한 특수 공백(줄바꿈 없는 공백  , 전각 공백 　)과 탭·줄바꿈도 같이 뺌
    // |는 검색용 이름(search_name)의 구분자라서 검색어에서 뺌 (안 빼면 "|"만 쳐도 전부 걸림)
    private static final Pattern IGNORED = Pattern.compile("[\\s\\u00A0\\u3000|]");

    public FoodPageResponse search(String q, int page, int size) {
        String keyword = IGNORED.matcher(q).replaceAll("").toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) {
            return FoodPageResponse.from(Page.empty(PageRequest.of(page, size)));
        }
        String pattern = "%" + escapeLike(keyword) + "%";
        return FoodPageResponse.from(foodRepository.search(keyword, pattern, PageRequest.of(page, size)));
    }

    // 상세. 식품코드는 글자 그대로 찾음 (숫자로 바꾸지 않음)
    public FoodResponse get(String code) {
        return foodRepository.findById(code)
                .map(FoodResponse::from)
                .orElseThrow(() -> new CustomException(FoodErrorCode.FOOD_NOT_FOUND));
    }

    // 검색어에 든 % _ \ 를 글자 그대로 찾게 막음 (PostgreSQL LIKE 기본 이스케이프 문자가 \)
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
