package com.dangdang.domain.visit.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 진료 메모 등록·수정 요청. 내용은 적은 그대로 저장함 (받아쓰기 결과도 글자로 보냄)
public record VisitNoteRequest(
        @NotBlank(message = "메모를 입력해 주세요.")
        @Size(max = 5000, message = "메모는 5000자까지 쓸 수 있어요.")
        String content) {

    // @NotBlank는 전각 공백(　)을 글자로 봐서 통과시킴 → 공백뿐인 메모를 막으려고 strip()으로 한 번 더 봄
    // 저장은 여전히 적은 그대로 (앞뒤 공백을 빼지 않음). null은 @NotBlank가 따로 막음
    @JsonIgnore
    @AssertTrue(message = "메모를 입력해 주세요.")
    public boolean isContentNotEmpty() {
        return content == null || !content.strip().isEmpty();
    }
}
