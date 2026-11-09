package com.dangdang.domain.reading.dto.response;

import com.dangdang.domain.reading.entity.Reading;

import java.time.Instant;
import java.util.List;

// 혈당 기록 목록 한 페이지 응답 (커서 방식)
// 다음 페이지는 nextCursorAt, nextCursorId 를 그대로 cursorAt, cursorId 로 보내면 됨
public record ReadingPageResponse(
        List<ReadingResponse> items,   // 이번 페이지 기록들 (최신순)
        boolean hasNext,               // 더 있는지
        Instant nextCursorAt,          // 다음 요청에 보낼 커서 (없으면 null)
        Long nextCursorId) {

    // fetched = DB에서 size + 1개 요청해서 받은 결과. 넘치면 다음 페이지가 있음
    public static ReadingPageResponse of(List<Reading> fetched, int size) {
        boolean hasNext = fetched.size() > size;
        List<Reading> page = hasNext ? fetched.subList(0, size) : fetched;

        List<ReadingResponse> items = page.stream().map(ReadingResponse::from).toList();
        if (!hasNext) {
            return new ReadingPageResponse(items, false, null, null);
        }
        Reading last = page.get(page.size() - 1);   // 이번 페이지 마지막 기록 = 다음 커서
        return new ReadingPageResponse(items, true, last.getMeasuredAt(), last.getId());
    }
}
