package com.dangdang.domain.insulin.dto.response;

import com.dangdang.domain.insulin.entity.InsulinLog;
import org.springframework.data.domain.Slice;

import java.util.List;

// 인슐린 기록 목록 한 페이지 (전체 개수는 안 셈, 다음 페이지가 있는지만)
public record InsulinLogPageResponse(
        List<InsulinLogResponse> items,   // 결과가 없으면 []
        int page,                         // 0부터
        int size,
        boolean hasNext) {

    public static InsulinLogPageResponse from(Slice<InsulinLog> slice) {
        return new InsulinLogPageResponse(
                slice.getContent().stream().map(InsulinLogResponse::from).toList(),
                slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
