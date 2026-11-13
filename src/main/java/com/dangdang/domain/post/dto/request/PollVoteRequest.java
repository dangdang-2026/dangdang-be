package com.dangdang.domain.post.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PollVoteRequest(
        @NotNull(message = "항목을 골라 주세요.")
        @Min(value = 0, message = "항목 번호를 확인해 주세요.")
        Integer optionIndex) {
}
