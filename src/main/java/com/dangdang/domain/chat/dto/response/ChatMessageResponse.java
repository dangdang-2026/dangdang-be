package com.dangdang.domain.chat.dto.response;

import com.dangdang.domain.chat.entity.ChatMessage;
import com.dangdang.domain.chat.entity.ChatPayload;
import com.dangdang.domain.chat.enums.ChatKind;
import com.dangdang.domain.chat.enums.ChatRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "ASSISTANT") ChatRole role,
        @Schema(description = "화면이 그릴 종류", example = "RECORDS") ChatKind kind,
        @Schema(example = "요청하신 기록이에요.") String content,
        @Schema(description = "RECORDS일 때 기록 카드 (Spring이 센 숫자)") ChatPayload.RecordsCard records,
        @Schema(description = "QUESTION_DRAFT일 때 물어볼 말 초안") String questionDraft,
        @Schema(description = "INFO일 때 출처") List<ChatPayload.Source> sources,
        @Schema(description = "RISK·DOSE일 때 전화 버튼 CALL_HOSPITAL · CALL_119 · CALL_109 · CALL_1577_0199")
        List<String> actions,
        @Schema(description = "도움이 됐나요 (안 눌렀으면 null)") Boolean helpful,
        Instant createdAt) {

    public static ChatMessageResponse from(ChatMessage m) {
        ChatPayload p = m.getPayload();
        return new ChatMessageResponse(m.getId(), m.getRole(), m.getKind(), m.getContent(),
                p == null ? null : p.records(),
                p == null ? null : p.questionDraft(),
                p == null ? null : p.sources(),
                p == null ? null : p.actions(),
                m.getHelpful(), m.getCreatedAt());
    }
}
