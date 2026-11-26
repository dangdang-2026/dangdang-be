package com.dangdang.domain.food.service;

import com.dangdang.domain.food.entity.Food;
import com.dangdang.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// 식품코드로 주식 여부·표시 단위를 찾음 (식사 항목·같은 음식 지난 기록·내 음식이 같이 씀). 규칙은 Food.isStaple()·unitLabel() 하나
// 코드가 없거나(직접 적은 음식) 음식 DB에 없으면 Traits.NONE (주식 아님, 인분) — 서버가 분류를 모르는 음식
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodStapleReader {

    private final FoodRepository foodRepository;

    // 음식 DB에서 읽어 응답에 붙이는 값
    public record Traits(boolean isStaple, String unitLabel) {
        public static final Traits NONE = new Traits(false, Food.DEFAULT_UNIT);

        static Traits of(Food f) {
            return new Traits(f.isStaple(), f.unitLabel());
        }
    }

    // 여러 코드를 한 번에 읽음 (식사 여러 개의 항목을 줄 때 항목마다 조회하지 않게). 맵에 없는 코드 = Traits.NONE
    // 쓰는 쪽이 getOrDefault(null 코드, NONE)로 부르므로 null 키 조회가 되는 HashMap으로 돌려줌 (Map.of()는 null 키에서 예외)
    public Map<String, Traits> byCodes(Collection<String> foodCodes) {
        List<String> codes = foodCodes.stream().filter(Objects::nonNull).distinct().toList();
        if (codes.isEmpty()) {
            return new HashMap<>();
        }
        return foodRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(Food::getFoodCode, Traits::of, (a, b) -> a, HashMap::new));
    }

    public Traits of(String foodCode) {
        return foodCode == null ? Traits.NONE : foodRepository.findById(foodCode).map(Traits::of).orElse(Traits.NONE);
    }
}
