package com.dangdang.domain.community.dto.response;

import java.util.List;

public record BlockListResponse(List<BlockedProfileResponse> items, String nextCursor, boolean hasNext) {
}
