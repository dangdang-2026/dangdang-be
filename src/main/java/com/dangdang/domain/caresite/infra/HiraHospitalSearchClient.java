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
//   search    : 이름으로 찾기 (DB가 비어 있을 때만 씀)
//   fetchPage : 전체 목록 한 쪽 (DB 적재용)
@Slf4j
@Component
public class HiraHospitalSearchClient implements HospitalSearchClient, HospitalListClient {

    private static final String URL = "https://apis.data.go.kr/B551182/hospInfoServicev2/getHospBasisList";

    private final HiraProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient searchClient;   // 사용자가 기다리는 검색: 10초
    private final RestClient bulkClient;     // 적재: 1,000줄 한 쪽에 55초쯤 걸림(11/22 실측) -> 2분

    public HiraHospitalSearchClient(HiraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.searchClient = restClient(10_000);
        this.bulkClient = restClient(120_000);
    }

    private static RestClient restClient(int readTimeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);              // 연결 3초
        factory.setReadTimeout(readTimeoutMillis);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public HospitalSearchResult search(String keyword, int page, int size) {
        // 값은 {변수}로 넣어야 키 속 +, /, = 와 한글이 제대로 인코딩됨
        return call(searchClient,
                URL + "?ServiceKey={key}&yadmNm={name}&pageNo={page}&numOfRows={size}&_type=json",
                Map.of("key", key(), "name", keyword, "page", page, "size", size),
                "병원 검색");
    }

    @Override
    public HospitalSearchResult fetchPage(int page, int size) {
        return call(bulkClient,
                URL + "?ServiceKey={key}&pageNo={page}&numOfRows={size}&_type=json",
                Map.of("key", key(), "page", page, "size", size),
                "전체 목록 " + page + "쪽");
    }

    private String key() {
        if (!properties.hasServiceKey()) {
            log.error("[HIRA] HIRA_SERVICE_KEY가 비어 있음");
            throw new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE);
        }
        return properties.serviceKey();
    }

    private HospitalSearchResult call(RestClient client, String uri, Map<String, ?> vars, String what) {
        try {
            String body = client.get().uri(uri, vars).retrieve().body(String.class);
            return parse(body);
        } catch (RestClientException | JacksonException e) {   // 키 오류(403)·시간 초과(504)·이상한 응답 전부
            log.error("[HIRA] {} 실패 - {}", what, e.getMessage());
            throw new CustomException(CareSiteErrorCode.HOSPITAL_SEARCH_UNAVAILABLE);
        }
    }

    // 응답 읽기. items 모양이 결과 수에 따라 다름
    //   0개 -> "items": ""   /   1개 -> "item": { }   /   여러 개 -> "item": [ ]
    // 심평원이 바쁘면 200이 아니라 504 + {"OpenAPI_ServiceResponse": ...}가 옴 -> 위에서 RestClientException으로 잡힘
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
