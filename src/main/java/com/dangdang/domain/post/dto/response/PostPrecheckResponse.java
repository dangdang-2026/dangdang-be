package com.dangdang.domain.post.dto.response;

import com.dangdang.domain.post.infra.PostCheckClient;
import java.util.List;

// issues가 비어 있으면 바로 올리고, 있으면 kind로 시트를 고른다 (DOSE → U-15, URGENT_MEDICAL·MIND → U-30, LOW_GLUCOSE → U-30c)
public record PostPrecheckResponse(List<Issue> issues, boolean checked) {

    public record Issue(String kind, String quote) {}

    public static PostPrecheckResponse from(PostCheckClient.Result result) {
        List<Issue> issues = result.issues() == null ? List.of()
                : result.issues().stream().map(i -> new Issue(i.kind(), i.quote())).toList();
        return new PostPrecheckResponse(issues, true);
    }

    // AI 서버가 꺼졌거나 늦으면: 글쓰기를 막지 않는다
    public static PostPrecheckResponse notChecked() {
        return new PostPrecheckResponse(List.of(), false);
    }
}
