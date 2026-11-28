package com.dangdang.domain.meal.service;

import com.dangdang.domain.food.entity.Food;
import com.dangdang.domain.food.exception.FoodErrorCode;
import com.dangdang.domain.food.repository.FoodRepository;
import com.dangdang.domain.food.service.FoodStapleReader;
import com.dangdang.domain.meal.dto.request.MealItemCreateRequest;
import com.dangdang.domain.meal.dto.request.MealItemNutritionRequest;
import com.dangdang.domain.meal.dto.request.MealItemUpdateRequest;
import com.dangdang.domain.meal.dto.response.MealItemResponse;
import com.dangdang.domain.meal.dto.response.MealItemSummaryResponse;
import com.dangdang.domain.meal.entity.Meal;
import com.dangdang.domain.meal.entity.MealItem;
import com.dangdang.domain.meal.exception.MealErrorCode;
import com.dangdang.domain.meal.repository.MealItemRepository;
import com.dangdang.domain.meal.repository.MealRepository;
import com.dangdang.domain.shop.entity.Product;
import com.dangdang.domain.shop.exception.ShopErrorCode;
import com.dangdang.domain.shop.repository.ProductRepository;
import com.dangdang.domain.user.service.SensitiveConsentGuard;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 식사에 붙는 음식 항목
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealItemService {

    private final MealRepository mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodRepository foodRepository;
    private final FoodStapleReader foodStapleReader;
    private final ProductRepository productRepository;
    private final Clock clock;
    private final SensitiveConsentGuard sensitiveConsentGuard;

    // 항목 추가. 식약처 음식(foodCode)이나 상점 상품(productId)을 고르거나 이름(name)을 직접 적음 (셋 중 하나만)
    @Transactional
    public MealItemResponse add(Long userId, Long mealId, MealItemCreateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        Meal meal = findMyMeal(userId, mealId);
        String foodCode = req.trimmedFoodCode();
        String name = req.trimmedName();
        Long productId = req.productId();
        int chosen = (foodCode != null ? 1 : 0) + (name != null ? 1 : 0) + (productId != null ? 1 : 0);
        if (chosen != 1) {
            throw new CustomException(MealErrorCode.ITEM_FOOD_OR_NAME);
        }

        MealItem item;
        if (foodCode != null) {
            // 고른 음식의 영양값은 공공 값을 복사함. 고치려면 영양값 직접 입력 API로 (명세대로 따로 둠)
            if (req.nutrition() != null) {
                throw new CustomException(MealErrorCode.ITEM_NUTRITION_WITH_FOOD);
            }
            Food food = foodRepository.findById(foodCode)
                    .orElseThrow(() -> new CustomException(FoodErrorCode.FOOD_NOT_FOUND));
            item = MealItem.ofFood(meal, food, req.servingsOrDefault(), req.weightG());
        } else if (productId != null) {
            // 상점 상품은 그 상품의 1개 영양값을 복사함. 개수로만 적음 (무게를 받으면 1개 값과 맞출 수 없음)
            if (req.nutrition() != null) {
                throw new CustomException(MealErrorCode.ITEM_NUTRITION_WITH_FOOD);
            }
            if (req.weightG() != null) {
                throw new CustomException(MealErrorCode.ITEM_PRODUCT_WEIGHT);
            }
            Product product = productRepository.findByIdAndActiveTrue(productId)
                    .orElseThrow(() -> new CustomException(ShopErrorCode.PRODUCT_NOT_FOUND));
            item = MealItem.ofProduct(meal, product, req.servingsOrDefault());
        } else {
            item = MealItem.ofCustom(meal, name, req.servingsOrDefault(), req.weightG(),
                    req.nutrition() == null ? null : req.nutrition().toNutrition());
        }
        mealItemRepository.save(item);
        return MealItemResponse.from(item, touchMeal(mealId), traits(item));
    }

    // 항목 수정 (보낸 칸만). 인분·무게, 직접 적은 음식이면 이름까지
    @Transactional
    public MealItemResponse update(Long userId, Long mealId, Long itemId, MealItemUpdateRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        if (req.isEmpty()) {
            throw new CustomException(MealErrorCode.NOTHING_TO_UPDATE);
        }
        MealItem item = findMyItem(userId, mealId, itemId);
        String name = req.trimmedName();
        if (name != null && !item.isCustom()) {
            throw new CustomException(MealErrorCode.ITEM_NAME_OF_FOOD);
        }
        if (item.getProductId() != null && req.getWeightG() != null) {   // 상점 상품은 개수로만
            throw new CustomException(MealErrorCode.ITEM_PRODUCT_WEIGHT);
        }
        item.update(req.getServings(), req.isWeightGSent(), req.getWeightG(), name);
        return MealItemResponse.from(item, touchMeal(mealId), traits(item));
    }

    // 영양값 직접 입력 (노션 명세). nutritionSource=USER가 되고, 적은 칸만 사용자 값으로 씀
    // expectedUpdatedAt이 식사의 지금 수정 시각과 다르면 그 사이 다른 곳에서 바뀐 것이라 409 (덮어쓰지 않게)
    @Transactional
    public MealItemResponse overrideNutrition(Long userId, Long mealId, Long itemId, MealItemNutritionRequest req) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        if (req.getNutritionOverride().toNutrition().isEmpty()) {
            throw new CustomException(MealErrorCode.ITEM_NUTRITION_EMPTY);
        }
        // 식사 줄을 잠그고 확인해서, 확인한 뒤 쓰기 전에 다른 요청이 끼어들지 못하게 함 (두 요청이 동시에 와도 하나는 409)
        Meal meal = findMyMeal(userId, mealId);
        if (!sameMoment(meal.getUpdatedAt(), req.getExpectedUpdatedAt())) {
            throw new CustomException(MealErrorCode.MEAL_CHANGED);
        }
        MealItem item = findItemOf(mealId, itemId);
        if (item.getProductId() != null && req.getWeightG() != null) {   // 상점 상품은 개수로만
            throw new CustomException(MealErrorCode.ITEM_PRODUCT_WEIGHT);
        }
        item.overrideNutrition(req.getNutritionOverride().toNutrition(), req.isWeightGSent(), req.getWeightG());
        return MealItemResponse.from(item, touchMeal(mealId), traits(item));
    }

    // 공공 영양값으로 되돌리기 (노션 명세). 직접 적은 값을 지우고 nutritionSource=DB
    // 영양값은 지금 foods를 다시 읽지 않고, 추가할 때 복사해 둔 값으로 계산함 (지난 기록은 그대로)
    // 직접 적은 음식은 공공 값이 없어서 409, 이미 공공 값이면 그냥 200
    @Transactional
    public MealItemResponse revertNutrition(Long userId, Long mealId, Long itemId) {
        sensitiveConsentGuard.require(userId);   // 건강정보 동의가 없으면 403
        MealItem item = findMyItem(userId, mealId, itemId);
        if (item.isCustom()) {
            throw new CustomException(MealErrorCode.NO_PUBLIC_NUTRITION);
        }
        if (!item.revertNutrition()) {
            // 이미 공공 값이라 바뀐 게 없음 → 식사 수정 시각도 그대로 (다른 기기가 괜히 409를 받지 않게)
            return MealItemResponse.from(item, item.getMeal().getUpdatedAt(), traits(item));
        }
        return MealItemResponse.from(item, touchMeal(mealId), traits(item));
    }

    // 항목 삭제 (soft delete). 이미 지운 항목을 또 지우면 404
    @Transactional
    public void delete(Long userId, Long mealId, Long itemId) {
        findMyItem(userId, mealId, itemId).delete(clock.instant());
        touchMeal(mealId);
    }

    // 여러 식사의 항목을 한 번에 읽어서 식사 id별로 나눔 (식사 상세·목록·오늘 화면·기록 통합 조회용)
    // 항목이 없는 식사는 맵에 없음 → 쓰는 쪽에서 getOrDefault(id, List.of())
    public Map<Long, List<MealItemSummaryResponse>> itemsByMeal(Collection<Long> mealIds) {
        if (mealIds.isEmpty()) {
            return Map.of();
        }
        List<MealItem> items = mealItemRepository.findAllByMealIdInAndDeletedAtIsNullOrderByIdAsc(mealIds);
        // 주식 여부는 음식을 한 번에 읽어서 붙임 (항목마다 음식을 따로 조회하지 않게)
        Map<String, FoodStapleReader.Traits> staple = foodStapleReader.byCodes(items.stream().map(MealItem::getFoodCode).toList());
        return items.stream()
                .collect(Collectors.groupingBy(i -> i.getMeal().getId(),
                        Collectors.mapping(i -> MealItemSummaryResponse.from(i, i.getProductId() != null
                                ? FoodStapleReader.Traits.PRODUCT
                                : staple.getOrDefault(i.getFoodCode(), FoodStapleReader.Traits.NONE)), Collectors.toList())));
    }

    // 항목 하나의 주식 여부·양 부르는 말. 상점 상품은 「개」
    private FoodStapleReader.Traits traits(MealItem item) {
        return item.getProductId() != null ? FoodStapleReader.Traits.PRODUCT : foodStapleReader.of(item.getFoodCode());
    }

    // 식사 하나의 항목
    public List<MealItemSummaryResponse> itemsOf(Long mealId) {
        return itemsByMeal(List.of(mealId)).getOrDefault(mealId, List.of());
    }

    // 항목이 바뀌면 식사도 바뀐 것이라 식사 수정 시각을 지금으로 바꾸고, 그 시각을 돌려줌 (응답의 mealUpdatedAt)
    // DB가 마이크로초까지만 저장해서 맞춰 자름 → 클라이언트가 이 값을 expectedUpdatedAt으로 그대로 보내도 맞음
    // touch가 먼저 DB에 써서(flush) 항목 변경도 같이 반영되고, 응답에 항목의 새 updatedAt이 담김
    private Instant touchMeal(Long mealId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        mealRepository.touch(mealId, now);
        return now;
    }

    // 두 시각이 같은지 (1밀리초 안쪽 차이는 같다고 봄)
    // 식사 수정 응답의 updatedAt은 메모리 값(나노초)이고 DB는 마이크로초로 반올림해 저장해서, 아래 자릿수가 조금 다를 수 있음
    //   밀리초로 잘라 비교하면 반올림이 밀리초 경계를 넘을 때 멀쩡한 요청도 409가 남
    private static boolean sameMoment(Instant a, Instant b) {
        return Duration.between(a, b).abs().compareTo(Duration.ofMillis(1)) < 0;
    }

    // 내 식사의 살아 있는 항목. 식사·항목이 없거나 남의 것이면 404
    private MealItem findMyItem(Long userId, Long mealId, Long itemId) {
        findMyMeal(userId, mealId);
        return findItemOf(mealId, itemId);
    }

    // 이 식사에 붙은 살아 있는 항목 (식사가 내 것인지는 먼저 확인했을 때만 부름)
    private MealItem findItemOf(Long mealId, Long itemId) {
        return mealItemRepository.findByIdAndMealIdAndDeletedAtIsNull(itemId, mealId)
                .orElseThrow(() -> new CustomException(MealErrorCode.ITEM_NOT_FOUND));
    }

    // 내 식사 + 삭제 안 된 것만, 이 식사 줄을 잠그면서 찾음. 없음·삭제됨·남의 것은 전부 404
    // 항목을 바꾸는 요청은 모두 "식사 잠금 → 항목 변경" 순서로 잠가서, 요청끼리 서로 기다리다 막히는 일(데드락)이 없음
    //   잠금 순서가 섞이면 같은 항목에 수정과 영양값 입력이 동시에 올 때 DB가 한쪽을 끊어서 500이 남
    // 식사를 지우는 요청과도 순서대로 처리돼서, 지운 식사에 새 항목이 붙지 않음
    private Meal findMyMeal(Long userId, Long mealId) {
        return mealRepository.findMineForUpdate(mealId, userId)
                .orElseThrow(() -> new CustomException(MealErrorCode.MEAL_NOT_FOUND));
    }
}
