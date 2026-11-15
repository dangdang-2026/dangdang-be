package com.dangdang.domain.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostPrecheckRequest(
        @Size(max = 100, message = "제목은 100자까지 적을 수 있어요.")
        String title,                  // 아직 안 적었으면 비워도 된다
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 5000, message = "내용은 5000자까지 적을 수 있어요.")
        String content) {
}
