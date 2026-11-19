package com.dangdang.domain.visit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 진료 질문 등록 요청. source는 받지 않음 (서버가 USER로 정함)
public record VisitQuestionCreateRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 500, message = "질문은 500자까지 쓸 수 있어요.")
        String content,

        @PositiveOrZero(message = "정렬 순서는 0 이상이어야 해요.")
        Integer sortOrder) {         // 안 보내면 맨 뒤

    // 앞뒤 공백 뺀 질문
    public String trimmedContent() {
        return content.strip();
    }
}
