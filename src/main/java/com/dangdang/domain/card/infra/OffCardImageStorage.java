package com.dangdang.domain.card.infra;

import com.dangdang.domain.card.exception.CardErrorCode;
import com.dangdang.global.exception.CustomException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// S3 설정이 없는 곳(팀원 로컬, 테스트)에서도 서버가 뜨게 하는 자리. 올리기만 막는다
@Component
@ConditionalOnProperty(name = "card-image.storage", havingValue = "off", matchIfMissing = true)
public class OffCardImageStorage implements CardImageStorage {

    @Override
    public String put(String key, byte[] bytes, String contentType) {
        throw new CustomException(CardErrorCode.IMAGE_STORAGE_OFF);
    }
}
