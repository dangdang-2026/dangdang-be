package com.dangdang.domain.chat.entity;

import com.dangdang.domain.chat.enums.ChatUnknownReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 글 말고 화면에 더 그릴 것 (JSONB 한 칸). 종류마다 필요한 것만 채우고 나머지는 null
// 칸을 늘리거나 줄여도 예전 대화 줄을 읽다가 실패하지 않게 ignoreUnknown (PR #88 리뷰 체크리스트)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatPayload(
        RecordsCard records,          // RECORDS
        String questionDraft,         // QUESTION_DRAFT
        List<Source> sources,         // INFO
        List<String> actions,         // RISK·DOSE: CALL_HOSPITAL · CALL_119 · CALL_109 · CALL_1577_0199 · CALL_112 · CALL_1366
        Risk risk,                    // RISK: 위급 안내 틀 (예전 줄은 null)
        ChatUnknownReason reason) {   // UNKNOWN: 이유 (예전 줄은 null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecordsCard(LocalDate from, LocalDate to, String slot, int count, List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Instant measuredAt, String slot, String readingType, int value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(String title, String url) {
    }

    // 위급 안내 한 틀 (Q-07 · Q-07b · Q-07c). type: URGENT · HYPO · MIND · SAFETY, warning = 빨간 「…바로 119」 줄
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Risk(String type, String title, String warning) {
    }

    public static ChatPayload ofRecords(RecordsCard card) {
        return new ChatPayload(card, null, null, null, null, null);
    }

    public static ChatPayload ofDraft(String draft) {
        return new ChatPayload(null, draft, null, null, null, null);
    }

    public static ChatPayload ofSources(List<Source> sources) {
        return new ChatPayload(null, null, sources, null, null, null);
    }

    public static ChatPayload ofActions(List<String> actions) {
        return new ChatPayload(null, null, null, actions, null, null);
    }

    public static ChatPayload ofRisk(Risk risk, List<String> actions) {
        return new ChatPayload(null, null, null, actions, risk, null);
    }

    public static ChatPayload ofReason(ChatUnknownReason reason) {
        return new ChatPayload(null, null, null, null, null, reason);
    }
}
