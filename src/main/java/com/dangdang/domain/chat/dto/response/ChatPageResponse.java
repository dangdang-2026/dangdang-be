package com.dangdang.domain.chat.dto.response;

import java.util.List;

// nextCursorId: 다음 장을 부를 때 cursorId로 그대로 보낸다 (더 없으면 null)
public record ChatPageResponse(List<ChatMessageResponse> items, int size, boolean hasNext, Long nextCursorId) {
}
