package com.dangdang.domain.user.dto.request;

import com.dangdang.domain.pregnancy.enums.CareMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CareModeUpdateRequest(
        @Schema(description = "관리 모드 DIET(식사 관리) · INSULIN(인슐린 함께) · ORAL(먹는 약 함께). 대문자 그대로", example = "INSULIN")
        @NotNull(message = "관리 모드를 골라 주세요.")
        CareMode careMode) {}
