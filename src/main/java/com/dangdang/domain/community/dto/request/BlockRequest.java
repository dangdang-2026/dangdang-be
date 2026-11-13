package com.dangdang.domain.community.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BlockRequest(
        @NotNull(message = "안 볼 사람을 골라 주세요.")
        @Positive(message = "프로필 번호를 확인해 주세요.")
        Long blockedProfileId) {       // JSON은 "102"처럼 글자로 와도 된다
}
