package com.dangdang.domain.myfood.service;

import com.dangdang.domain.food.repository.FoodRepository;
import com.dangdang.domain.food.service.FoodStapleReader;
import com.dangdang.domain.meal.dto.request.MealNutritionRequest;
import com.dangdang.domain.meal.entity.MealNutrition;
import com.dangdang.domain.myfood.dto.request.MyFoodCreateRequest;
import com.dangdang.domain.myfood.dto.request.MyFoodUpdateRequest;
import com.dangdang.domain.myfood.dto.response.MyFoodResponse;
import com.dangdang.domain.myfood.entity.MyFood;
import com.dangdang.domain.myfood.exception.MyFoodErrorCode;
import com.dangdang.domain.myfood.repository.MyFoodRepository;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.exception.UserErrorCode;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;

// 내 음식 (MY-04b). 나만 보인다. 지워도 지난 끼니 기록은 그대로 — 끼니(meal_items, 언니)는 이름&값을 따로 저장한다
@Service
@RequiredArgsConstructor
public class MyFoodService {

    private final MyFoodRepository myFoodRepository;
    private final FoodRepository foodRepository;
    private final FoodStapleReader foodStapleReader;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MyFoodResponse> list(Long userId, String keyword) {
        List<MyFood> foods = keyword == null || keyword.isBlank()
                ? myFoodRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId)
                : myFoodRepository.findByUserIdAndNameContainingIgnoreCaseOrderByUpdatedAtDescIdDesc(userId, keyword.strip());
        // 주식 여부는 비슷한 음식(similarFoodCode)을 한 번에 읽어서 따름 (없으면 false)
        Map<String, FoodStapleReader.Traits> staple = foodStapleReader.byCodes(foods.stream().map(MyFood::getSimilarFoodCode).toList());
        return foods.stream().map(f -> MyFoodResponse.from(f, staple.getOrDefault(f.getSimilarFoodCode(), FoodStapleReader.Traits.NONE))).toList();
    }

    @Transactional
    public MyFoodResponse create(Long userId, MyFoodCreateRequest req) {
        String name = req.name().strip();
        if (myFoodRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new CustomException(MyFoodErrorCode.DUPLICATE_NAME);
        }
        String similar = checkedFoodCode(req.similarFoodCode());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        MyFood food = MyFood.of(user, name, similar,
                req.usualQuantity(), req.usualGrams(), nutrition(req.nutritionOverride()), clock.instant());
        try {
            myFoodRepository.saveAndFlush(food);   // 같은 이름이 동시에 오면 여기서 DB UNIQUE(ux_my_foods_user_name)가 막음
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(MyFoodErrorCode.DUPLICATE_NAME);
        }
        return MyFoodResponse.from(food, foodStapleReader.of(food.getSimilarFoodCode()));
    }

    @Transactional
    public MyFoodResponse update(Long userId, Long id, MyFoodUpdateRequest req) {
        if (req.isEmpty()) {
            throw new CustomException(MyFoodErrorCode.EMPTY_CHANGE);
        }
        if (req.hasValueAndClear()) {
            throw new CustomException(MyFoodErrorCode.VALUE_AND_CLEAR);
        }
        MyFood food = mine(userId, id);
        String name = req.name() == null ? null : req.name().strip();
        if (name != null && myFoodRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw new CustomException(MyFoodErrorCode.DUPLICATE_NAME);
        }
        food.change(name, checkedFoodCode(req.similarFoodCode()), req.clears("similarFoodCode"),
                req.usualQuantity(), req.clears("usualQuantity"),
                req.usualGrams(), req.clears("usualGrams"),
                nutrition(req.nutritionOverride()), req.clears("nutritionOverride"), clock.instant());
        try {
            myFoodRepository.flush();   // 이름 바꾸기가 동시에 겹쳐도 DB UNIQUE가 막음
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(MyFoodErrorCode.DUPLICATE_NAME);
        }
        return MyFoodResponse.from(food, foodStapleReader.of(food.getSimilarFoodCode()));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        myFoodRepository.delete(mine(userId, id));
    }

    private MyFood mine(Long userId, Long id) {
        return myFoodRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CustomException(MyFoodErrorCode.NOT_FOUND));
    }

    // 비슷한 음식은 식약처 음식(foods, #81)에 있는 코드만 — 없는 코드면 DB 외래키 500 대신 404
    private String checkedFoodCode(String code) {
        String c = blankToNull(code);
        if (c != null && !foodRepository.existsById(c)) {
            throw new CustomException(MyFoodErrorCode.SIMILAR_FOOD_NOT_FOUND);
        }
        return c;
    }

    // 영양값 객체를 보냈으면 한 칸은 있어야 한다 (안 보냈으면 null)
    private static MealNutrition nutrition(MealNutritionRequest req) {
        if (req == null) {
            return null;
        }
        MealNutrition n = req.toNutrition();
        if (n.isEmpty()) {
            throw new CustomException(MyFoodErrorCode.EMPTY_NUTRITION);
        }
        return n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
