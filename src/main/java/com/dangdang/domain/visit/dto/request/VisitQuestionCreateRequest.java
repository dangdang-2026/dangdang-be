package com.dangdang.domain.visit.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

// 진료 질문 등록 요청. source는 받지 않음 (서버가 USER로 정함)
public record VisitQuestionCreateRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        String content,

        @PositiveOrZero(message = "정렬 순서는 0 이상이어야 해요.")
        Integer sortOrder) {         // 안 보내면 맨 뒤

    private static final int MAX_CONTENT = 500;

    // 앞뒤 공백 뺀 질문
    public String trimmedContent() {
        return content.strip();
    }

    // 길이는 앞뒤 공백을 뺀 뒤로 잼 (저장하는 것도 뺀 값이라). 비었으면 @NotBlank가 따로 막음
    @JsonIgnore
    @AssertTrue(message = "질문은 500자까지 쓸 수 있어요.")
    public boolean isContentWithinLimit() {
        return content == null || content.strip().length() <= MAX_CONTENT;
    }
}
