package com.dangdang.domain.shop.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 「판매처로 이동」 — 기록을 남긴 뒤 이 주소로 보내면 된다 (DB에 있는 https 주소만)
public record ProductOutboundResponse(
        @Schema(example = "https://shop.example.com/goods/123") String url) {
}
