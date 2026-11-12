package com.dangdang.domain.caresite.infra;

import com.dangdang.domain.caresite.exception.CareSiteErrorCode;
import com.dangdang.global.config.property.HiraProperties;
import com.dangdang.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 심평원 병원정보서비스 > 병원기본목록(getHospBasisList) 호출
@Slf4j
@Component
public class HiraHospitalSearchClient implements HospitalSearchClient {

    private static final String URL = "https://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList";

    private final HiraProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HiraHospitalSearchClient(HiraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);   // 연결 3초
        factory.setReadTimeout(10_000);     // 응답 10초. 심평원이 느릴 때가 많지만 더 기다리게 하진 않고 직접 입력으로 안내함
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public HospitalSearchResult search(String keyword, int page, int size) {
        if (!properties.hasServiceKey()) {
            log.error("[HIRA] HIRA_SERVICE_KEY가 비어 있음");
            throw new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE);
        }
        try {
            // 값은 {변수}로 넣어야 키 속 +, /, = 와 한글이 제대로 인코딩됨
            String body = restClient.get()
                    .uri(URL + "?ServiceKey={key}&yadmNm={name}&pageNo={page}&numOfRows={size}&_type=json",
                            Map.of("key", properties.serviceKey(), "name", keyword,
                                    "page", page, "size", size))
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RestClientException | JacksonException e) {   // 키 오류(403)·시간 초과·이상한 응답 전부
            log.error("[HIRA] 병원 검색 실패 - {}", e.getMessage());
            throw new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE);
        }
    }

    // 응답 읽기. items 모양이 결과 수에 따라 다름
    //   0개 -> "items": ""   /   1개 -> "item": { }   /   여러 개 -> "item": [ ]
    private HospitalSearchResult parse(String body) {
        JsonNode response = objectMapper.readTree(body).path("response");
        String resultCode = response.path("header").path("resultCode").asString("");
        if (!"00".equals(resultCode)) {
            log.error("[HIRA] 정상 응답 아님 - resultCode={}, msg={}",
                    resultCode, response.path("header").path("resultMsg").asString(""));
            throw new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE);
        }
        JsonNode bodyNode = response.path("body");
        JsonNode item = bodyNode.path("items").path("item");

        List<HospitalSearchResult.Hospital> hospitals = new ArrayList<>();
        if (item.isArray()) {
            item.forEach(node -> hospitals.add(toHospital(node)));
        } else if (item.isObject()) {
            hospitals.add(toHospital(item));
        }
        return new HospitalSearchResult(hospitals, bodyNode.path("totalCount").asInt(0));
    }

    private HospitalSearchResult.Hospital toHospital(JsonNode n) {
        return new HospitalSearchResult.Hospital(
                text(n, "ykiho"), text(n, "yadmNm"), text(n, "clCdNm"), text(n, "addr"), text(n, "telno"));
    }

    // 없는 칸은 null
    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asString();
    }
}
