package com.dangdang.domain.visit.entity;

import com.dangdang.domain.criteria.enums.MeasureSlot;
import com.dangdang.domain.visit.enums.ExtractStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

// 진료 메모 AI 정리 결과 (visit_notes.ai_extract JSONB에 그대로 저장, 응답에도 그대로 나감)
// 메모에 없는 칸은 null (AI가 지어내지 않음). 처방·약 용량은 뽑지 않음
// 모르는 칸이 와도 읽다가 실패하지 않게 ignoreUnknown (AI 서버가 칸을 늘려도 저장된 결과를 계속 읽을 수 있게)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VisitNoteExtract(
        Field<List<MeasureSlot>> measureSlots,   // 잴 때 (하루 순서)
        Field<Integer> fastingTarget,            // 공복 목표 (미만)
        Field<Integer> postMealTarget,           // 식후 목표 (미만)
        Field<Integer> postMealMinutes,          // 식후 몇 분에 잴지 (30~180)
        Field<LocalDate> nextVisitDate,          // 다음 진료일
        boolean insulinSkipped,                  // 인슐린·약 문장이 있어서 AI에 안 보냄 → 「약은 직접 적어 주세요」
        String promptVersion) {                  // 어떤 프롬프트 판으로 정리했는지

    // 한 칸: 뽑은 값 + 근거 문장(메모에 있는 그대로) + 확신 정도
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field<T>(T value, String evidence, ExtractStatus status) {
    }
}
