package com.dangdang.domain.chat.dto.response;

// 보낸 말과 당당이 답 한 쌍 (화면에 바로 두 말풍선을 그린다)
public record ChatAskResponse(ChatMessageResponse question, ChatMessageResponse answer) {
}
