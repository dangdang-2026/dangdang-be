package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NameUpdateRequest(
        @Schema(description = "부를 이름 (앞뒤 공백을 지운 뒤 1~10자)", example = "다현")
        @NotNull(message = "이름은 1~10자로 적어 주세요.")
        @Pattern(regexp = "^\\P{Cntrl}{1,10}$", message = "이름은 1~10자로 적어 주세요.")
        String name,
        @Schema(description = "GET /api/me 에서 받은 version 그대로 (BIGINT 문자열)", example = "0")
        @NotNull(message = "version을 보내 주세요.")
        @Pattern(regexp = "^\\d{1,18}$", message = "version은 숫자만 적어 주세요.")
        String version) {

    // @Valid 검사보다 먼저 실행된다 -> 공백을 지운 값으로 글자 수를 잰다
    public NameUpdateRequest {
        name = name == null ? null : name.strip();
    }
}
