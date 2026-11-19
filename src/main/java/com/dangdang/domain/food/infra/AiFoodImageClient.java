package com.dangdang.domain.food.infra;

import com.dangdang.global.config.property.AiProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AiFoodImageClient implements FoodImageClient {

    private final AiProperties properties;
    private final RestClient restClient;

    public AiFoodImageClient(AiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);  // 연결 2초
        factory.setReadTimeout(10_000);    // 응답 10초 (사진 1장 약 0.1초라 넉넉함, 명세 권장값)
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    // AI 서버 /internal/food-candidates 에 사진을 multipart(필드 image)로 보냄
    // 사진은 메모리에서만 넘기고 서버에 저장하지 않음. 사용자 쿠키는 보내지 않음
    @Override
    public Result candidates(byte[] image, String filename, String contentType) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(contentType != null
                ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM);
        // 파일 이름이 있어야 multipart에서 "파일"로 보내짐 (없으면 AI 서버가 image 필드를 글자로 받음)
        ByteArrayResource file = new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "image";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new HttpEntity<>(file, partHeaders));

        return restClient.post()
                .uri("/internal/food-candidates")
                .header("X-Internal-Token", properties.internalToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Result.class);
    }
}
