package com.dangdang.domain.mealplan.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// 받은 표현 그대로 (MEAL-09). 계산·비교·달성 표시 없음
public record MealPlanRequest(
        @Schema(description = "받은 곳 — 내 병원 번호 (선택)", example = "3") Long careSiteId,
        @Schema(description = "받은 곳 — 병원 목록에 없는 곳을 글로 (선택, 앞뒤 공백을 지운 뒤 50자까지, 한 줄, 병원 번호와 같이 보내도 됨)",
                example = "○○보건소 영양 교실")
        @Pattern(regexp = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$", message = "받은 곳은 한 줄로 적어 주세요.") String placeName,
        @Schema(description = "받은 날 (선택)", example = "2026-11-10") LocalDate receivedOn,
        @Schema(description = "끼니 안내 원문 (2000자)", example = "아침: 잡곡밥 2/3공기, 나물 반찬 두 가지…")
        @Size(max = 2000, message = "끼니 안내는 2000자까지 적을 수 있어요.") String mealGuide,
        @Schema(description = "간식 안내 원문 (1000자)", example = "오전·오후 간식: 우유 1팩 또는 사과 반 개")
        @Size(max = 1000, message = "간식 안내는 1000자까지 적을 수 있어요.") String snackGuide) {

    // 받은 곳: 빈 글은 null, 나머지는 앞뒤 공백을 지움 (@Valid 검사보다 먼저 실행, 연락 기준 메모와 같은 방식)
    // 글자 수(50자)는 이모지를 한 글자로 세려고 @Size 대신 MealPlanService에서 코드 포인트로 잰다
    public MealPlanRequest {
        placeName = placeName == null || placeName.isBlank() ? null : placeName.strip();
    }

    @AssertTrue(message = "끼니나 간식 안내를 적어 주세요.")
    public boolean isAnyGuide() {
        return (mealGuide != null && !mealGuide.isBlank()) || (snackGuide != null && !snackGuide.isBlank());
    }
}
