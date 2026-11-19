package com.dangdang.domain.post.enums;

import com.dangdang.domain.pregnancy.enums.PregnancyState;

import java.util.List;

// 인기 게시판 「대상」 (U-25) — 글쓴이의 지금 임신 상태로 고른다
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
