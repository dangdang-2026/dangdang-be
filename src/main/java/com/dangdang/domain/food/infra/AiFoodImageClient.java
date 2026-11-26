package com.dangdang.domain.food.infra;

import com.dangdang.global.config.property.AiProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AiFoodImageClient implements FoodImageClient {

    private static final String FILENAME = "image.jpg";

    private final AiProperties properties;
    private final RestClient restClient;

    public AiFoodImageClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);  // 연결 2초
        factory.setReadTimeout(10_000);    // 응답 10초 (한상 사진은 그릇 찾기 + 그릇마다 분류라 1~3초, 명세 권장값)
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    // AI 서버 /internal/food-candidates 에 사진을 multipart(필드 image)로 보냄
    // 사진은 메모리에서만 넘기고 저장하지 않음. 사용자 쿠키는 보내지 않음
    @Override
    public Result candidates(String requestId, byte[] image, String contentType) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(safeMediaType(contentType));
        // 파일 이름이 있어야 multipart에서 "파일"로 보내짐 (없으면 AI 서버가 image 필드를 글자로 받음)
        // AI 서버는 이름을 안 보므로 사용자 파일 이름(한글·따옴표 등) 대신 고정 이름을 씀
        ByteArrayResource file = new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return FILENAME;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new HttpEntity<>(file, partHeaders));

        return restClient.post()
                .uri("/internal/food-candidates")
                .header("X-Internal-Token", properties.internalToken())
                .header("X-Request-Id", requestId)        // AI 서버 로그와 맞춰 보는 번호 (챗봇과 같은 방식)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Result.class);
    }

    // 사용자가 보낸 형식 값이 없거나 깨졌으면("image", "jpeg;;" 등) 500 대신 그냥 파일(octet-stream)로 보냄
    // 사진인지는 AI 서버가 내용을 열어 보고 판단함
    private MediaType safeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
