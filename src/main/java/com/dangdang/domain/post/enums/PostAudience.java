package com.dangdang.domain.post.enums;

import com.dangdang.domain.pregnancy.enums.PregnancyState;

import java.util.List;

// 인기 게시판 「대상」 (U-25) — 글쓴이의 지금 임신 상태로 고른다
// 다른 상황(PAUSED)은 어느 대상에도 안 넣는다 — 상황을 짐작할 수 없게 「전체」에서만 보인다
public enum PostAudience {
    ALL(List.of()),
    PREGNANT(List.of(PregnancyState.PREGNANT, PregnancyState.HOSPITALIZED)),   // 임신 중
    POSTPARTUM(List.of(PregnancyState.POSTPARTUM, PregnancyState.ENDED));      // 출산 후

    private final List<PregnancyState> states;

    PostAudience(List<PregnancyState> states) {
        this.states = states;
    }

    public List<PregnancyState> states() {
        return states;
    }
}
