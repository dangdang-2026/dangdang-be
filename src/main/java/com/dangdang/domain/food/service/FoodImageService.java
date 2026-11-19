package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodImageAnalysisResponse;
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
import java.util.List;
import java.util.UUID;

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

        List<FoodImageClient.Candidate> candidates = cleanUp(result);
        if (candidates.isEmpty() || candidates.get(0).confidence() < MIN_CONFIDENCE) {
            log.info("[FoodImage] 사용자: {}, 확신 낮음 - 이름으로 적기 requestId={}", userId, requestId);
            return FoodImageAnalysisResponse.fallbackToName();
        }
        log.info("[FoodImage] 사용자: {}, 후보 {}개 requestId={}", userId, candidates.size(), requestId);

        // AI 이름(AI Hub)으로 음식 검색을 그대로 씀 → 이름이 똑같은 음식이 맨 앞, 그다음 가정식·외식 순
        return FoodImageAnalysisResponse.of(candidates.stream()
                .map(c -> new FoodImageAnalysisResponse.Candidate(c.name(), c.confidence(),
                        foodService.search(c.name(), 0, FOODS_PER_CANDIDATE).items()))
                .toList());
    }

    // AI 응답을 그대로 믿지 않음: 이름이 비었거나 확신도가 0~1 밖(퍼센트 45, NaN 등)인 후보는 빼고,
    // 확신도 높은 순으로 다시 세운 뒤 3개까지만
    // (명세상 AI가 정렬된 3개를 주지만, AI 서버가 바뀌어도 500이나 엉뚱한 판단이 나지 않게)
    private List<FoodImageClient.Candidate> cleanUp(FoodImageClient.Result result) {
        if (result == null || result.candidates() == null) {
            return List.of();
        }
        return result.candidates().stream()
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
