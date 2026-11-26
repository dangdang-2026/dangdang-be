package com.dangdang.domain.card.infra;

import com.dangdang.domain.card.exception.CardErrorCode;
import com.dangdang.global.config.property.S3Properties;
import com.dangdang.global.exception.CustomException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// 키는 AWS 기본 방식으로 읽는다: 환경 변수 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY, 없으면 EC2 역할
@Slf4j
@Component
@ConditionalOnProperty(name = "card-image.storage", havingValue = "s3")
public class S3CardImageStorage implements CardImageStorage {

    // 파일 이름이 매번 새로 생기니(무작위) 한 번 받은 이미지는 다시 받을 필요가 없다
    private static final String CACHE = "public, max-age=31536000, immutable";

    private final S3Client s3;
    private final String bucket;
    private final String baseUrl;

    public S3CardImageStorage(S3Properties props) {
        if (!StringUtils.hasText(props.bucket()) || !StringUtils.hasText(props.region())) {
            throw new IllegalStateException("card-image.storage=s3 인데 S3_BUCKET·S3_REGION 값이 비어 있어요.");
        }
        this.s3 = S3Client.builder().region(Region.of(props.region())).build();
        this.bucket = props.bucket();
        String base = StringUtils.hasText(props.publicBaseUrl())
                ? props.publicBaseUrl()
                : "https://" + props.bucket() + ".s3." + props.region() + ".amazonaws.com";
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public String put(String key, byte[] bytes, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(contentType).cacheControl(CACHE).build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {   // 키가 틀림, 버킷 권한 없음, 연결 실패
            log.error("[CardImage] S3 올리기 실패 key={}: {}", key, e.getMessage());
            throw new CustomException(CardErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return baseUrl + "/" + key;
    }

    @PreDestroy
    void close() {
        s3.close();
    }
}
