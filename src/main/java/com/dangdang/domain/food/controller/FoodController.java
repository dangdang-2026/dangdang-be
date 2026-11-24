package com.dangdang.domain.food.controller;

import com.dangdang.domain.food.dto.response.FoodHistoryPageResponse;
import com.dangdang.domain.food.dto.response.FoodImageAnalysisResponse;
import com.dangdang.domain.food.dto.response.FoodPageResponse;
import com.dangdang.domain.food.dto.response.FoodResponse;
import com.dangdang.domain.food.service.FoodHistoryService;
import com.dangdang.domain.food.service.FoodImageService;
import com.dangdang.domain.food.service.FoodService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Food", description = "음식 (식약처 음식 영양성분) API")
@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;
    private final FoodHistoryService foodHistoryService;
    private final FoodImageService foodImageService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 음식 검색 ]",
            description =
                    """
                    **Parameters** q(필수, 1~100자, 식품명·대표식품명 부분 검색, 띄어쓰기 무시, "볶음밥_김치"처럼 순서가 바뀐 이름도 "김치볶음밥"으로 찾음), page(기본 0, 0~10000), size(기본 20, 1~100)
                    **Returns** items[]{foodCode, name, displayName, origin, maker, servingG, servingUnit(g·ml), kcal, carbG, proteinG, fatG, sugarG, sourceVersion}·page·size·totalElements·hasNext.
                    name은 식약처 원래 이름(볶음밥_김치), displayName은 화면용 이름(김치 볶음밥). 화면에는 displayName을 씀.
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
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
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
                    **Returns** foodCode, name, displayName, origin, maker, servingG, servingUnit(g·ml), kcal, carbG, proteinG, fatG, sugarG, sourceVersion.
                    displayName·origin·maker와 영양값 규칙은 검색과 같음 (1인분 기준, servingG가 null이면 100g(ml)당, 모르는 값은 null).
                    없는 코드면 FOOD_40401, 30자를 넘으면 GLOBAL_40001
                    """)
    @GetMapping("/{code}")
    public ResponseEntity<BaseResponse<FoodResponse>> get(
            @PathVariable @Size(max = 30, message = "식품코드는 30자까지예요.") String code) {
        return ResponseEntity.ok(BaseResponse.success(200, "음식 상세 조회에 성공했습니다.",
                foodService.get(code)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 같은 음식 지난 기록 ]",
            description =
                    """
                    **Parameters** code(식약처 식품코드, 1~30자), page(기본 0, 0~10000), size(기본 20, 1~100)
                    **Returns** items[]{mealId, startedAt, mealSlot, items[](이 식사에서 이 음식을 먹은 항목: 먹은 양·영양값), readings[](이 식사에 연결된 혈당, 잰 시각 순. 혈당 상세와 같은 칸: meterLimit·판정 칸·expectedAt·timing·originalValue·editedAt)}·page·size·totalElements·hasNext.
                    내가 이 음식을 먹은 식사만, 식사 시작 시각 최신순. 지운 식사·항목은 빠짐. 한 식사에 같은 음식이 여러 번 있어도 식사 1개로 묶음.
                    혈당은 식사에 연결된(mealId) 것만 붙임 (시간이 가까워도 연결 안 된 혈당은 안 붙임).
                    먹은 적이 없는 코드면 items=[] (식약처 데이터에서 사라진 코드여도 지난 기록은 보여 줌).
                    30자를 넘거나 page·size가 범위 밖이면 GLOBAL_40001
                    """)
    @GetMapping("/{code}/history")
    public ResponseEntity<BaseResponse<FoodHistoryPageResponse>> history(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 30, message = "식품코드는 30자까지예요.") String code,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "같은 음식 지난 기록 조회에 성공했습니다.",
                foodHistoryService.history(principal.userId(), code, page, size)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 음식 사진 AI 분석 ]",
            description =
                    """
                    **Parameters** multipart/form-data, image(사진 1장, 필수, 10MB까지, JPG·PNG 등. HEIC는 JPEG로 바꿔서 보냄. 긴 변 1600px 정도로 줄여 보내기 권장)
                    **Returns** fallback, candidates[]{name, confidence(0~1), foods[](그 이름으로 음식 검색한 앞쪽 3개, 검색 결과와 같은 모양, 못 찾으면 [])}.
                    candidates는 확신도 높은 순 최대 3개. name은 AI 음식 이름이라 식약처 이름과 다를 수 있어서 foods에서 골라 씀.
                    fallback=true면 후보 화면 대신 「이름으로 적기」로 넘어감 (1등 확신도 0.7 미만, AI 서버 실패·시간 초과). 이때 candidates=[].
                    사진 AI 분석 동의(AI_PHOTO)를 안 했으면 FOOD_40301 (사진을 AI 서버로 보내지 않음, 동의 화면으로 안내).
                    사진은 저장하지 않음. image가 없거나 비었으면 FOOD_40001, 사진으로 열 수 없는 파일이면 FOOD_40002,
                    10MB를 넘으면 GLOBAL_41301, multipart가 아니면 GLOBAL_41501
                    """)
    @PostMapping(value = "/analyze-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<FoodImageAnalysisResponse>> analyzeImage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart(value = "image", required = false) MultipartFile image) {   // 없을 때 400으로 답하려고 서비스에서 확인
        return ResponseEntity.ok(BaseResponse.success(200, "음식 사진 분석에 성공했습니다.",
                foodImageService.analyze(principal.userId(), image)));
    }
}
