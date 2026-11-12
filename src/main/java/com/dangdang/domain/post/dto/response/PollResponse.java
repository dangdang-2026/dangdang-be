package com.dangdang.domain.post.dto.response;

import java.util.List;
import java.util.stream.IntStream;

public record PollResponse(List<PollOptionResponse> options, long totalVotes, Integer myOptionIndex) {

    // 19단계(투표)를 만들기 전까지는 항목만 내려간다
    public static PollResponse notVoted(List<String> options) {
        List<PollOptionResponse> items = IntStream.range(0, options.size())
                .mapToObj(i -> new PollOptionResponse(i, options.get(i), null))
                .toList();
        return new PollResponse(items, 0, null);
    }
}
