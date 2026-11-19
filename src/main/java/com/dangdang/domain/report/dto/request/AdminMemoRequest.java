package com.dangdang.domain.report.dto.request;

import jakarta.validation.constraints.Size;

public record AdminMemoRequest(
        @Size(max = 200, message = "메모는 200자까지 적을 수 있어요.")
        String memo) {
}
