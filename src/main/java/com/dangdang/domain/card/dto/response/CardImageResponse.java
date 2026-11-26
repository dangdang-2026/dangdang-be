package com.dangdang.domain.card.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 올린 카드 이미지. 운영자 화면은 크기를 보고 4:5(1080×1350)가 아니면 알려 준다
public record CardImageResponse(
        @Schema(description = "슬라이드 imageUrl 에 넣을 주소") String url,
        @Schema(example = "1080") int width,
        @Schema(example = "1350") int height) {
}
