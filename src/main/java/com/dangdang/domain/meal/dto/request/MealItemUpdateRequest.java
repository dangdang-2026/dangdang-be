package com.dangdang.domain.meal.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// 식사 항목 수정 요청 (PATCH) — 보낸 칸만 바뀜, 안 보낸 칸은 그대로 둠
// weightG만 "안 보냄(그대로)"과 "null을 보냄(무게를 지우고 인분으로 계산)"을 구분해야 해서 record 대신 class로 둠
// 음식 자체(foodCode)는 못 바꿈. 다른 음식이면 지우고 새로 추가
@Getter
@NoArgsConstructor
public class MealItemUpdateRequest {

    @DecimalMin(value = "0", inclusive = false, message = "먹은 양(인분)은 0보다 커야 해요.")
    @DecimalMax(value = "20", message = "먹은 양(인분)은 20인분까지예요.")
    @Digits(integer = 2, fraction = 2, message = "먹은 양(인분)은 소수 둘째 자리까지예요.")
    private BigDecimal servings;

    @DecimalMin(value = "0", inclusive = false, message = "먹은 무게는 0보다 커야 해요.")
    @DecimalMax(value = "9999.9", message = "먹은 무게는 9999.9까지예요.")
    @Digits(integer = 4, fraction = 1, message = "먹은 무게는 소수 첫째 자리까지예요.")
    private BigDecimal weightG;

    @JsonIgnore   // 서버 내부 표시라 JSON으로 받지 않음 (안 막으면 "weightGSent": true만 보내도 무게가 지워짐)
    private boolean weightGSent;     // weightG 칸을 보냈는지 (null을 보낸 것도 보낸 것)

    @Size(max = 100, message = "음식 이름은 100자까지예요.")
    private String name;             // 직접 적은 음식만 바꿀 수 있음

    public void setServings(BigDecimal servings) {
        this.servings = servings;
    }

    @JsonProperty("weightG")
    public void setWeightG(BigDecimal weightG) {
        this.weightG = weightG;
        this.weightGSent = true;
    }

    public void setName(String name) {
        this.name = name;
    }

    // 앞뒤 공백 뺀 이름 (빈 이름은 안 보낸 것으로 봄)
    public String trimmedName() {
        return name == null || name.isBlank() ? null : name.trim();
    }

    // 바꿀 값이 하나도 없는 요청인지. Swagger·JSON에 "empty" 칸으로 안 보이게 뺌
    @JsonIgnore
    public boolean isEmpty() {
        return servings == null && !weightGSent && trimmedName() == null;
    }
}
