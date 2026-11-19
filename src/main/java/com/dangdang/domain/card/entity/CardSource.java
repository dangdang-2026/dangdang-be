package com.dangdang.domain.card.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 마지막 장 출처 (기관·자료 이름 + 주소)
@JsonIgnoreProperties(ignoreUnknown = true)   // JSONB에 모르는 칸이 있어도 읽기가 깨지지 않게
public record CardSource(
        @Schema(example = "대한당뇨병학회 임신성 당뇨병 진료지침") @NotBlank @Size(max = 100) String title,
        @Schema(example = "https://www.diabetes.or.kr") @NotBlank @Size(max = 500) String url) {
}
