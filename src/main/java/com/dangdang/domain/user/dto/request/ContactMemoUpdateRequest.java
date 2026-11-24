package com.dangdang.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactMemoUpdateRequest(
        @Schema(description = "연락 기준 메모 (앞뒤 공백을 지운 뒤 100자까지, 한 줄). 비우거나 null이면 지운다",
                example = "같은 시점에 계속 높게 나오면 외래로 전화하기")
        @Size(max = 100, message = "연락 기준 메모는 100자까지 적을 수 있어요.")
        @Pattern(regexp = "^\\P{Cntrl}*$", message = "연락 기준 메모는 한 줄로 적어 주세요.")
        String contactMemo) {

    // @Valid 검사보다 먼저 실행된다 -> 공백을 지운 값으로 글자 수를 재고, 빈 글은 「지우기」(null)로 바꾼다
    public ContactMemoUpdateRequest {
        contactMemo = contactMemo == null || contactMemo.isBlank() ? null : contactMemo.strip();
    }
}
