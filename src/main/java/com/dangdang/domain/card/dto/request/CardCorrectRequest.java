package com.dangdang.domain.card.dto.request;

import com.dangdang.domain.card.entity.Slide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CardCorrectRequest(
        @Schema(description = "무엇을 고쳤는지 한 줄 (카드 위에 보임)", example = "2장 식후 측정 시간을 학회 원문대로 고쳤어요.")
        @NotBlank @Size(max = 200) String note,
        @Schema(description = "고친 슬라이드 전체 (안 보내면 그대로)") @Size(min = 1, max = 10) List<@Valid Slide> body,
        @Schema(description = "다시 확인한 날 (안 보내면 그대로)") LocalDate checkedOn) {
}
