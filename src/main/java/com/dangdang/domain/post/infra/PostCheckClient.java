package com.dangdang.domain.post.infra;

import java.util.List;

// 글 올리기 전 검사를 AI 서버에 맡기는 창구 (테스트에서는 가짜로 바꾼다)
public interface PostCheckClient {

    // allowLlm=false면 AI 서버가 규칙만 쓴다 (GPT·해외로 안 보냄) — 커뮤니티 AI 확인 동의가 없을 때 (83 H)
    Result check(String title, String content, boolean allowLlm);

    record Result(List<Issue> issues, boolean llmChecked) {}

    record Issue(String kind, String quote) {}   // kind: DOSE · URGENT_MEDICAL · MIND · LOW_GLUCOSE · SAFETY
}
