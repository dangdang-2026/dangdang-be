package com.dangdang.domain.post.dto.response;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public record PollResponse(List<PollOptionResponse> options, long totalVotes, Integer myOptionIndex) {

    // counts를 null로 주면 항목별 숫자를 숨긴다 — 투표 전에는 결과를 안 보여 준다 (U-02 -> U-09)
    public static PollResponse of(List<String> options, Map<Long, Long> counts, long totalVotes, Integer myOptionIndex) {
        List<PollOptionResponse> items = IntStream.range(0, options.size())
                .mapToObj(i -> new PollOptionResponse(i, options.get(i),
                        counts == null ? null : counts.getOrDefault((long) i, 0L)))
                .toList();
        return new PollResponse(items, totalVotes, myOptionIndex);
    }
}
