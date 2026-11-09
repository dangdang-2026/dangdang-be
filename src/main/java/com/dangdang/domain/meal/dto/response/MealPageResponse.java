package com.dangdang.domain.meal.dto.response;

import com.dangdang.domain.meal.entity.Meal;

import java.time.Instant;
import java.util.List;

// 식사 목록 한 페이지 응답 (커서 방식)
// 다음 페이지는 nextCursorAt, nextCursorId 를 그대로 cursorAt, cursorId 로 보내면 됨
public record MealPageResponse(
        List<MealResponse> items,      // 이번 페이지 식사들 (최신순)
        boolean hasNext,               // 더 있는지
        Instant nextCursorAt,          // 다음 요청에 보낼 커서 (없으면 null)
        Long nextCursorId) {

    // fetched = DB에서 size + 1개 요청해서 받은 결과. 넘치면 다음 페이지가 있음
    public static MealPageResponse of(List<Meal> fetched, int size) {
        boolean hasNext = fetched.size() > size;
        List<Meal> page = hasNext ? fetched.subList(0, size) : fetched;

        List<MealResponse> items = page.stream().map(MealResponse::from).toList();
        if (!hasNext) {
            return new MealPageResponse(items, false, null, null);
        }
        Meal last = page.get(page.size() - 1);   // 이번 페이지 마지막 식사 = 다음 커서
        return new MealPageResponse(items, true, last.getStartedAt(), last.getId());
    }
}
