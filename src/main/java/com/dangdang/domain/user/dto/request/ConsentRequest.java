package com.dangdang.domain.user.dto.request;

import com.dangdang.domain.user.enums.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConsentRequest(
        @Schema(description = "바꿀 동의 항목들") @NotEmpty(message = "동의 항목을 하나 이상 보내 주세요.")
        List<@Valid Item> items) {

    public record Item(
            @Schema(description = "동의 항목", example = "TERMS") @NotNull(message = "동의 항목을 골라 주세요.")
            ConsentType type,
            @Schema(description = "동의하면 true, 거부·철회면 false", example = "true")
            @NotNull(message = "동의 여부를 보내 주세요.")
            Boolean granted) {}
}
