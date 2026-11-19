package com.dangdang.domain.visit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 진료 메모 등록·수정 요청. 내용은 적은 그대로 저장함 (받아쓰기 결과도 글자로 보냄)
public record VisitNoteRequest(
        @NotBlank(message = "메모를 입력해 주세요.")
        @Size(max = 5000, message = "메모는 5000자까지 쓸 수 있어요.")
        String content) {
}
