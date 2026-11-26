package com.dangdang.domain.card.infra;

// 카드뉴스 이미지 저장소. card-image.storage=s3 이면 S3, 아니면 꺼져 있다 (올리면 CARD_50301)
public interface CardImageStorage {

    // key 자리에 올리고, 앱이 받을 https 주소를 돌려준다
    String put(String key, byte[] bytes, String contentType);
}
