package com.dangdang.domain.post.dto.request;

import com.dangdang.domain.post.enums.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
        @NotNull(message = "게시판을 골라 주세요.")
        PostCategory category,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자까지 적을 수 있어요.")
        String title,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 5000, message = "내용은 5000자까지 적을 수 있어요.")
        String content,

        Boolean isAnonymous,

        @Size(min = 2, max = 5, message = "투표 항목은 2~5개로 만들어 주세요.")
        List<@NotBlank(message = "빈 투표 항목이 있어요.")
        @Size(max = 50, message = "투표 항목은 50자까지 적을 수 있어요.") String> poll) {
}
