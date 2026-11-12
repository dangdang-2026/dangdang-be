package com.dangdang.domain.post.dto.request;

import com.dangdang.domain.post.enums.PostCategory;
import jakarta.validation.constraints.Size;

import java.util.List;

// 쓰다 만 글이라 전부 비어 있어도 된다. 길이만 막는다
public record PostDraftRequest(
        PostCategory category,

        @Size(max = 100, message = "제목은 100자까지 적을 수 있어요.")
        String title,

        @Size(max = 5000, message = "내용은 5000자까지 적을 수 있어요.")
        String content,

        Boolean isAnonymous,

        @Size(max = 5, message = "투표 항목은 최대 5개예요.")
        List<@Size(max = 50, message = "투표 항목은 50자까지 적을 수 있어요.") String> poll) {
}
