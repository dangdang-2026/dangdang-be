package com.dangdang.domain.chat.infra;

import java.time.LocalDate;
import java.util.List;

// 챗봇 질문을 AI 서버(POST /internal/chat)에 맡기는 창구 (테스트에서는 가짜로 바꾼다)
// AI는 "무엇을 해 줄지"만 정한다. 기록 숫자는 Spring이 직접 센다 (ASK-03)
public interface ChatClient {

    Reply ask(String requestId, String message, boolean recordsAllowed, List<Turn> history);

    record Turn(String role, String text) {}          // role: user · assistant

    // kind: RECORDS · QUESTION_DRAFT · INFO · UNKNOWN
    // reason: UNKNOWN일 때 이유 (ChatUnknownReason 이름, 예전 AI면 null)
    record Reply(String kind, String text, Records records, String questionDraft, List<Source> sources,
                 String reason, String promptVersion) {}

    record Records(LocalDate from, LocalDate to, String slot) {}   // slot: null이면 전체

    record Source(String title, String url) {}
}
