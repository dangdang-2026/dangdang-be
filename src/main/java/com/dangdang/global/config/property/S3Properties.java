package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 카드뉴스 이미지를 올릴 S3. 키(AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY)는 여기 두지 않고 AWS 기본 방식으로 읽는다
// publicBaseUrl: 앱이 이미지를 받을 주소 앞부분. 비우면 버킷 기본 주소 (https://{bucket}.s3.{region}.amazonaws.com)
@ConfigurationProperties("s3")
public record S3Properties(String bucket, String region, String publicBaseUrl) {
}
