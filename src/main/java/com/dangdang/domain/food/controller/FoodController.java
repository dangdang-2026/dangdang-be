package com.dangdang.domain.food.controller;

import com.dangdang.domain.food.dto.response.FoodPageResponse;
import com.dangdang.domain.food.dto.response.FoodResponse;
import com.dangdang.domain.food.service.FoodService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Food", description = "음식 (식약처 음식 영양성분) API")
@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 음식 검색 ]",
            description =
                    """
                    **Parameters** q(필수, 1~100자, 식품명·대표식품명 부분 검색, 띄어쓰기 무시, "볶음밥_김치"처럼 순서가 바뀐 이름도 "김치볶음밥"으로 찾음), page(기본 0), size(기본 20, 1~100)
                    **Returns** items[]{foodCode, name, origin, maker, servingG, servingUnit(g·ml), kcal, carbG, proteinG, fatG, sugarG, sourceVersion}·page·size·totalElements·hasNext.
                    origin(HOME 가정식 · RESTAURANT 외식 · MEAL_SERVICE 급식 · FRANCHISE 프랜차이즈)은 같은 이름이 여러 줄일 때 구분용, maker는 프랜차이즈 업체명(나머지 null).
                    영양값은 1인분 기준(소수 첫째 자리). servingG가 null이면 1인분 양을 몰라서 영양값은 100g(ml)당. 모르는 영양값은 null(0과 다름).
                    순서: 이름이 똑같은 음식 → 가정식·외식 → 급식 → 프랜차이즈 → 이름순. 결과가 없으면 items=[].
                    q가 비었거나 100자를 넘거나 page·size가 범위 밖이면 GLOBAL_40001
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<FoodPageResponse>> search(
            @RequestParam
            @NotBlank(message = "검색어를 입력해 주세요.")
            @Size(max = 100, message = "검색어는 100자까지예요.") String q,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0부터예요.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "음식 검색에 성공했습니다.",
                foodService.search(q.trim(), page, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 음식 상세 ]",
            description =
                    """
                    **Parameters** code(식약처 식품코드, 1~30자, 검색 결과의 foodCode 그대로)
                    **Returns** foodCode, name, origin, maker, servingG, servingUnit(g·ml), kcal, carbG, proteinG, fatG, sugarG, sourceVersion.
                    origin·maker와 영양값 규칙은 검색과 같음 (1인분 기준, servingG가 null이면 100g(ml)당, 모르는 값은 null).
                    없는 코드면 FOOD_40401, 30자를 넘으면 GLOBAL_40001
                    """)
    @GetMapping("/{code}")
    public ResponseEntity<BaseResponse<FoodResponse>> get(
            @PathVariable @Size(max = 30, message = "식품코드는 30자까지예요.") String code) {
        return ResponseEntity.ok(BaseResponse.success(200, "음식 상세 조회에 성공했습니다.",
                foodService.get(code)));
    }
}
