package com.dangdang.domain.chat.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 글 말고 화면에 더 그릴 것 (JSONB 한 칸). 종류마다 필요한 것만 채우고 나머지는 null
public record ChatPayload(
        RecordsCard records,          // RECORDS
        String questionDraft,         // QUESTION_DRAFT
        List<Source> sources,         // INFO
        List<String> actions) {       // RISK·DOSE: CALL_HOSPITAL · CALL_119 · CALL_109 · CALL_1577_0199

    public record RecordsCard(LocalDate from, LocalDate to, String slot, int count, List<Item> items) {
    }

    public record Item(Instant measuredAt, String slot, String readingType, int value) {
    }

    public record Source(String title, String url) {
    }

    public static ChatPayload ofRecords(RecordsCard card) {
        return new ChatPayload(card, null, null, null);
    }

    public static ChatPayload ofDraft(String draft) {
        return new ChatPayload(null, draft, null, null);
    }

    public static ChatPayload ofSources(List<Source> sources) {
        return new ChatPayload(null, null, sources, null);
    }

    public static ChatPayload ofActions(List<String> actions) {
        return new ChatPayload(null, null, null, actions);
    }
}
