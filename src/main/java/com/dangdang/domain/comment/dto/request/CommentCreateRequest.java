package com.dangdang.domain.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @Positive(message = "부모 댓글 번호를 확인해 주세요.")
        Long parentId,                 // 없으면 그냥 댓글, 있으면 답글. JSON은 "301"처럼 글자로 와도 된다

        @NotBlank(message = "댓글을 입력해 주세요.")
        @Size(max = 1000, message = "댓글은 1000자까지 적을 수 있어요.")
        String content,

        Boolean isAnonymous) {
}
