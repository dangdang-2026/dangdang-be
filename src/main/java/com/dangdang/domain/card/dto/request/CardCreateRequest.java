package com.dangdang.domain.card.dto.request;

import com.dangdang.domain.card.entity.CardSource;
import com.dangdang.domain.card.entity.Slide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CardCreateRequest(
        @Schema(example = "식후 혈당, 언제 재나요?") @NotBlank @Size(max = 100) String title,
        @Schema(description = "BEFORE_VISIT · GUIDELINE · POSTPARTUM · MIND", example = "GUIDELINE")
        @Pattern(regexp = "BEFORE_VISIT|GUIDELINE|POSTPARTUM|MIND", message = "시리즈 이름을 다시 확인해 주세요.") String series,
        @Schema(description = "슬라이드 1~10장") @NotEmpty @Size(max = 10) List<@Valid Slide> body,
        @Schema(description = "출처 1개 이상 — 출처 없는 카드는 못 만든다") @NotEmpty List<@Valid CardSource> sources,
        @Schema(description = "U-28 「물어볼 것에 적기」 질문 0~5개 (안 보내면 빈 목록)",
                example = "[\"제 식후 측정 시간이 1시간인지 2시간인지 다시 확인하고 싶어요\"]")
        @Size(max = 5) List<@NotBlank @Size(max = 100) String> questions,
        @Schema(description = "원문을 확인한 날", example = "2026-11-14") @NotNull LocalDate checkedOn) {
}
