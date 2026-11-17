package com.dangdang.domain.postpartum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record PostpartumResultRequest(
        @Schema(description = "들은 그대로 적기 (500자, 비우면 null로 지움)", example = "정상 범위라고 하셨어요. 1년 뒤 다시 검사.")
        @Size(max = 500, message = "결과 메모는 500자까지 적을 수 있어요.")
        String resultNote) {
}
