package com.dangdang.domain.card.controller;

import com.dangdang.domain.card.dto.response.CardImageResponse;
import com.dangdang.domain.card.service.CardImageService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Card", description = "당당 소식 카드뉴스 API")
@RestController
@RequiredArgsConstructor
public class CardImageController {

    private final CardImageService cardImageService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 카드 이미지 올리기 ]",
            description =
                    """
                    **Parameters** multipart/form-data, image(한 장, 필수, PNG·JPG, 5MB까지. 1080×1350 권장)
                    **Returns** url, width, height. url을 슬라이드 imageUrl에 넣고 alt(이미지 속 글)를 같이 적어 카드 만들기·정정하기로 보낸다.
                    image가 없으면 CARD_40001, PNG·JPG가 아니면 CARD_40002, 5MB를 넘으면 GLOBAL_41301,
                    S3가 꺼진 서버면 CARD_50301, S3 올리기 실패면 CARD_50201
                    """)
    @PostMapping(value = "/api/admin/card-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<CardImageResponse>> upload(
            @RequestPart(value = "image", required = false) MultipartFile image) {   // 없을 때 400으로 답하려고 서비스에서 확인
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(201, "이미지를 올렸습니다.", cardImageService.upload(image)));
    }
}
