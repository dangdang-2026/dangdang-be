package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodImageAnalysisResponse;
import com.dangdang.domain.food.dto.response.FoodResponse;
import com.dangdang.domain.food.exception.FoodErrorCode;
import com.dangdang.domain.food.infra.FoodImageClient;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

// 음식 사진 AI 분석: AI 서버에서 후보를 받고, 후보 이름을 식약처 음식(foods)과 맞춤
// DB에 쓰지 않아서 @Transactional 이 필요 없음
// 사진은 저장하지도, 로그에 남기지도 않음. 업로드도 디스크 임시 파일 없이 메모리에서만 처리함 (multipart file-size-threshold 10MB)
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodImageService {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // AI 서버가 받는 최대 크기 10MB
    static final double MIN_CONFIDENCE = 0.7;                // 1등이 이보다 낮으면 「이름으로 적기」 (명세 2026-11-14 기준)
    private static final int MAX_CANDIDATES = 3;             // 돌려줄 후보 수
    private static final int FOODS_PER_CANDIDATE = 3;        // 후보 하나에 붙일 식약처 음식 수
    static final int MAX_ITEMS = 6;                           // 한상 사진에서 돌려줄 최대 음식 수 (AI 순서대로 앞에서부터)
    private static final double BOX_SLACK = 0.001;            // 위치 비율의 소수 오차 (x + width가 1.0000001처럼 살짝 넘는 것)
    private static final int MAX_LOGGED_BODY = 300;          // AI 오류 응답 본문을 로그에 남길 최대 글자 수

    private final FoodImageClient foodImageClient;
    private final FoodService foodService;
    private final ConsentService consentService;

    public FoodImageAnalysisResponse analyze(Long userId, MultipartFile image) {
        // 사진 AI 분석에 동의(AI_PHOTO, 선택 동의)하지 않았으면 사진을 AI 서버로 아예 보내지 않음
        if (!consentService.isGranted(userId, ConsentType.AI_PHOTO)) {
            throw new CustomException(FoodErrorCode.PHOTO_CONSENT_REQUIRED);
        }
        if (image == null || image.isEmpty()) {
            throw new CustomException(FoodErrorCode.IMAGE_REQUIRED);
        }
        // multipart 설정(max-file-size)에서 먼저 막히지만, 설정이 바뀌어도 AI 서버 한도를 넘기지 않게 한 번 더 봄
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_IMAGE_BYTES);
        }

        String requestId = UUID.randomUUID().toString();   // AI 서버 로그와 맞춰 보는 번호
        FoodImageClient.Result result;
        try {
            result = foodImageClient.candidates(requestId, image.getBytes(), image.getContentType());
        } catch (IOException e) {   // 업로드된 사진을 못 읽음 (서버 쪽 문제) → 다른 실패처럼 「이름으로 적기」
            log.warn("[FoodImage] 사진 읽기 실패 - 이름으로 적기 requestId={}: {}", requestId, e.getClass().getSimpleName());
            return FoodImageAnalysisResponse.fallbackToName();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {   // 사진으로 열 수 없는 파일 (HEIC 포함)
                throw new CustomException(FoodErrorCode.NOT_AN_IMAGE);
            }
            if (e.getStatusCode() == HttpStatus.CONTENT_TOO_LARGE) {   // AI 서버 한도가 우리보다 작아진 경우
                log.error("[FoodImage] AI 서버 용량 초과 requestId={} - BE·AI 한도가 어긋남", requestId);
                throw new CustomException(GlobalErrorCode.PAYLOAD_TOO_LARGE);
            }
            // 401(토큰)·422(요청 형식) 등은 사용자 문제가 아니라 BE↔AI 약속이 어긋난 것 → 알아챌 수 있게 error + 응답 본문
            log.error("[FoodImage] AI 서버 요청 거절 - 이름으로 적기 requestId={} status={} body={}",
                    requestId, e.getStatusCode(), truncate(e.getResponseBodyAsString()));
            return FoodImageAnalysisResponse.fallbackToName();
        } catch (RestClientException e) {   // 503(모델 없음)·연결 실패·시간 초과
            log.warn("[FoodImage] AI 서버 실패 - 이름으로 적기 requestId={}: {}", requestId, e.getClass().getSimpleName());
            return FoodImageAnalysisResponse.fallbackToName();
        }

        // 한상 사진(F-13): AI가 음식마다 위치를 줬으면 그걸 쓰고, 아직 안 줬으면(예전 계약) 사진 전체를 음식 하나로 봄
        // 빈 값을 먼저 걸러 낸 뒤 판단 → items가 [null]뿐이어도 예전 candidates로 처리
        List<FoodImageClient.Item> aiItems = result == null || result.items() == null
                ? List.of()
                : topItems(result.items().stream().filter(Objects::nonNull).toList());
        if (aiItems.isEmpty()) {
            aiItems = List.of(new FoodImageClient.Item(null, result == null ? null : result.candidates()));
        }

        // 같은 이름은 한 번만 검색 (한상이면 음식 6개 × 후보 3개라 겹치는 이름이 많음)
        Map<String, List<FoodResponse>> searched = new HashMap<>();
        List<FoodImageAnalysisResponse.Item> items = aiItems.stream().map(i -> toItem(i, searched)).toList();
        FoodImageAnalysisResponse response = FoodImageAnalysisResponse.of(items);
        if (response.fallback()) {
            log.info("[FoodImage] 사용자: {}, 확신 낮음 - 이름으로 적기 requestId={}", userId, requestId);
        } else {
            log.info("[FoodImage] 사용자: {}, 음식 {}개 requestId={}", userId, items.size(), requestId);
        }
        return response;
    }

    // 사진 속 음식 하나 → 후보를 정리하고, 1등 확신도가 낮으면 이 음식만 「이름으로 적기」
    // AI 이름(AI Hub)으로 음식 검색을 그대로 씀 → 이름이 똑같은 음식이 맨 앞, 그다음 가정식·외식 순
    private FoodImageAnalysisResponse.Item toItem(FoodImageClient.Item aiItem, Map<String, List<FoodResponse>> searched) {
        FoodImageAnalysisResponse.Box box = cleanBox(aiItem.box());
        List<FoodImageClient.Candidate> candidates = cleanUp(aiItem.candidates());
        if (candidates.isEmpty() || candidates.get(0).confidence() < MIN_CONFIDENCE) {
            return FoodImageAnalysisResponse.Item.of(box, true, List.of());
        }
        return FoodImageAnalysisResponse.Item.of(box, false, candidates.stream()
                .map(c -> new FoodImageAnalysisResponse.Candidate(c.name(), c.confidence(),
                        searched.computeIfAbsent(c.name(),
                                name -> foodService.search(name, 0, FOODS_PER_CANDIDATE).items())))
                .toList());
    }

    // 음식이 6개보다 많으면 1등 확신도가 높은 6개를 남김 (AI가 보낸 순서대로 자르면 확신 높은 음식이 빠질 수 있어서)
    // 남긴 음식은 AI가 보낸 순서 그대로 둠 (화면의 번호가 사진 위치 순서와 맞게)
    private static List<FoodImageClient.Item> topItems(List<FoodImageClient.Item> items) {
        if (items.size() <= MAX_ITEMS) {
            return items;
        }
        // 순서 번호로 고름 (위치·후보가 똑같은 음식이 여러 개면 값으로 비교할 때 같은 것으로 취급돼서)
        Set<Integer> keep = new HashSet<>(IntStream.range(0, items.size()).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> topConfidence(items.get(i))).reversed())
                .limit(MAX_ITEMS)
                .toList());
        return IntStream.range(0, items.size()).filter(keep::contains).mapToObj(items::get).toList();
    }

    // 이 음식의 1등 확신도 (후보가 없거나 이상하면 0)
    private static double topConfidence(FoodImageClient.Item item) {
        if (item.candidates() == null) {
            return 0;
        }
        return item.candidates().stream()
                .filter(c -> c != null && Double.isFinite(c.confidence()) && c.confidence() >= 0 && c.confidence() <= 1)
                .mapToDouble(FoodImageClient.Candidate::confidence)
                .max().orElse(0);
    }

    // 위치도 그대로 믿지 않음: 빈 값이거나 숫자가 아니거나 0~1 밖이거나 넓이가 0이면 위치 없음(null)으로 → 화면은 표시만 안 함
    private static FoodImageAnalysisResponse.Box cleanBox(FoodImageClient.Box b) {
        if (b == null || b.x() == null || b.y() == null || b.width() == null || b.height() == null) {
            return null;
        }
        double[] v = {b.x(), b.y(), b.width(), b.height()};
        for (double d : v) {
            if (!Double.isFinite(d) || d < 0 || d > 1) {
                return null;
            }
        }
        if (b.width() <= 0 || b.height() <= 0 || b.x() + b.width() > 1 + BOX_SLACK || b.y() + b.height() > 1 + BOX_SLACK) {
            return null;
        }
        return new FoodImageAnalysisResponse.Box(b.x(), b.y(), b.width(), b.height());
    }

    // AI 응답을 그대로 믿지 않음: 이름이 비었거나 확신도가 0~1 밖(퍼센트 45, NaN 등)인 후보는 빼고,
    // 확신도 높은 순으로 다시 세운 뒤 3개까지만
    // (명세상 AI가 정렬된 3개를 주지만, AI 서버가 바뀌어도 500이나 엉뚱한 판단이 나지 않게)
    private List<FoodImageClient.Candidate> cleanUp(List<FoodImageClient.Candidate> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(c -> c != null && c.name() != null && !c.name().isBlank())
                .filter(c -> Double.isFinite(c.confidence()) && c.confidence() >= 0 && c.confidence() <= 1)
                .sorted(Comparator.comparingDouble(FoodImageClient.Candidate::confidence).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_LOGGED_BODY ? s : s.substring(0, MAX_LOGGED_BODY) + "...";
    }
}
