package com.dangdang.domain.postpartum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PostpartumTestCreateRequest(
        @Schema(description = "병원에서 들은 검사 이름 (40자)", example = "75g 경구 당부하 검사")
        @NotBlank(message = "검사 이름을 적어 주세요.") @Size(max = 40, message = "검사 이름은 40자까지 적을 수 있어요.")
        String name,
        @Schema(description = "검사 날짜", example = "2026-12-20")
        @NotNull(message = "검사 날짜를 골라 주세요.")
        LocalDate testDate,
        @Schema(description = "어디서 하나요 (등록한 병원 이름 그대로 또는 직접 적기, 50자, 안 보내거나 빈 글이면 없음)", example = "○○산부인과")
        @Size(max = 50, message = "검사 장소는 50자까지 적을 수 있어요.")
        String placeName,
        @Schema(description = "전날 저녁 알림 (안 보내면 true)", example = "true") Boolean remindDayBefore,
        @Schema(description = "당일 아침 알림 (안 보내면 true)", example = "true") Boolean remindSameDay) {
}
