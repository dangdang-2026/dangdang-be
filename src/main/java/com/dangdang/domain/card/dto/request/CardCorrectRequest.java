package com.dangdang.domain.card.dto.request;

import com.dangdang.domain.card.entity.Slide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CardCorrectRequest(
        @Schema(description = "정정 요약 한 줄 (카드 위 띠)", example = "식후 측정 시간을 고쳤어요")
        @NotBlank @Size(max = 40) String summary,
        @Schema(description = "자세한 내용 (펼치면 보임, 선택)", example = "2장 식후 측정 시간을 학회 원문대로 고쳤어요.")
        @Size(max = 200) String note,
        @Schema(description = "고친 슬라이드 전체 (안 보내면 그대로)") @Size(min = 1, max = 10) List<@Valid Slide> body,
        @Schema(description = "고친 질문 목록 (안 보내면 그대로)") @Size(max = 5) List<@NotBlank @Size(max = 100) String> questions,
        @Schema(description = "다시 확인한 날 (안 보내면 그대로)") LocalDate checkedOn) {
}
