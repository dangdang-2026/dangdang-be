package com.dangdang.domain.post.dto.request;

import com.dangdang.domain.post.enums.PostCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        @Size(max = 50, message = "투표 항목은 50자까지 적을 수 있어요.") String> poll,

        // U-15 「그대로 올리기」 · U-30 「그래도 글 올리기」: 올리기 전 확인(precheck)의 issues[].kind 중 사용자가 보고도 올린 것
        // 예전 앱과 맞추려고 받기만 한다. 처리함에 넣을지는 서버가 기억한 확인 결과로 정한다 (PostService.queueForReview)
        @Schema(description = "올리기 전 확인에서 안내를 보고도 그대로 올린 kind (선택, 최대 5개). "
                + "보내지 않아도 된다. 같은 사람이 같은 본문으로 30분 안에 확인해서 걸린 종류는 서버가 알아서 운영자 처리함에 넣는다. "
                + "DOSE → MEDICAL_ADVICE, URGENT_MEDICAL · MIND · LOW_GLUCOSE · SAFETY → CRISIS",
                example = "[\"DOSE\"]")
        @Size(max = 5, message = "확인 종류를 다시 보내 주세요.")
        List<@NotNull(message = "확인 종류를 다시 보내 주세요.")
        @Pattern(regexp = "[A-Z_]{1,30}", message = "확인 종류를 다시 보내 주세요.") String> postedDespite) {
}
