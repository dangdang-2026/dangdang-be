package com.dangdang.domain.meal.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// 식사 항목 영양값 직접 입력 요청 (노션 명세)
// weightG는 "안 보냄(그대로)"과 "null을 보냄(무게 지우기)"을 구분해야 해서 record 대신 class로 둠
@Getter
@NoArgsConstructor
public class MealItemNutritionRequest {

    @DecimalMin(value = "0", inclusive = false, message = "먹은 무게는 0보다 커야 해요.")
    @DecimalMax(value = "9999.9", message = "먹은 무게는 9999.9까지예요.")
    @Digits(integer = 4, fraction = 1, message = "먹은 무게는 소수 첫째 자리까지예요.")
    private BigDecimal weightG;           // 영양성분표 기준 무게 (예: 300g에 탄수화물 45g)

    @JsonIgnore   // 서버 내부 표시라 JSON으로 받지 않음 (안 막으면 "weightGSent": true만 보내도 무게가 지워짐)
    private boolean weightGSent;          // weightG 칸을 보냈는지 (null을 보낸 것도 보낸 것)

    @NotNull(message = "직접 적을 영양값을 보내 주세요.")
    @Valid
    private MealNutritionRequest nutritionOverride;   // 먹은 양 전체 기준. 적은 칸만 바뀌고 안 적은 칸은 공공 값으로 계산

    @NotNull(message = "식사의 마지막 수정 시각(expectedUpdatedAt)을 보내 주세요.")
    private Instant expectedUpdatedAt;    // 화면을 열 때 받은 식사 updatedAt. 그 사이 다른 곳에서 바뀌었으면 409

    @JsonProperty("weightG")
    public void setWeightG(BigDecimal weightG) {
        this.weightG = weightG;
        this.weightGSent = true;
    }

    @JsonProperty("nutritionOverride")
    public void setNutritionOverride(MealNutritionRequest nutritionOverride) {
        this.nutritionOverride = nutritionOverride;
    }

    @JsonProperty("expectedUpdatedAt")
    public void setExpectedUpdatedAt(Instant expectedUpdatedAt) {
        this.expectedUpdatedAt = expectedUpdatedAt;
    }
}
