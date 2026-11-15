package com.dangdang.domain.post.infra;

import java.util.List;

// 글 올리기 전 검사를 AI 서버에 맡기는 창구 (테스트에서는 가짜로 바꾼다)
public interface PostCheckClient {

    Result check(String title, String content);

    record Result(List<Issue> issues, boolean llmChecked) {}

    record Issue(String kind, String quote) {}   // kind: DOSE · URGENT_MEDICAL · MIND
}
