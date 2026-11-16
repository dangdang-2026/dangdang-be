package com.dangdang.domain.chat.dto.response;

import java.util.List;

public record ChatPageResponse(List<ChatMessageResponse> items, int page, int size, boolean hasNext) {
}
