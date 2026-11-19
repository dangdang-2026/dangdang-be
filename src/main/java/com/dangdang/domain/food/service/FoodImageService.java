package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodImageAnalysisResponse;
import com.dangdang.domain.food.exception.FoodErrorCode;
import com.dangdang.domain.food.infra.FoodImageClient;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.service.ConsentService;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;

// 음식 사진 AI 분석: AI 서버에서 후보를 받고, 후보 이름을 식약처 음식(foods)과 맞춤
// DB에 쓰지 않아서 @Transactional 이 필요 없음. 사진은 저장하지도, 로그에 남기지도 않음
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodImageService {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // AI 서버가 받는 최대 크기 10MB
    static final double MIN_CONFIDENCE = 0.7;                // 1등이 이보다 낮으면 「이름으로 적기」 (명세 2026-11-14 기준)
    private static final int MAX_CANDIDATES = 3;             // 돌려줄 후보 수
    private static final int FOODS_PER_CANDIDATE = 3;        // 후보 하나에 붙일 식약처 음식 수

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

        FoodImageClient.Result result;
        try {
            result = foodImageClient.candidates(readBytes(image), image.getContentType());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {   // 사진으로 열 수 없는 파일 (HEIC 포함)
                throw new CustomException(FoodErrorCode.NOT_AN_IMAGE);
            }
            log.warn("[FoodImage] AI 서버 요청 실패 - 이름으로 적기: {}", e.getStatusCode());   // 토큰 틀림(401) 등
            return FoodImageAnalysisResponse.fallbackToName();
        } catch (RestClientException e) {   // 503(모델 없음)·연결 실패·시간 초과
            log.warn("[FoodImage] AI 서버 실패 - 이름으로 적기: {}", e.getClass().getSimpleName());
            return FoodImageAnalysisResponse.fallbackToName();
        }

        List<FoodImageClient.Candidate> candidates = cleanUp(result);
        if (candidates.isEmpty() || candidates.get(0).confidence() < MIN_CONFIDENCE) {
            log.info("[FoodImage] 사용자: {}, 확신 낮음 - 이름으로 적기", userId);
            return FoodImageAnalysisResponse.fallbackToName();
        }
        log.info("[FoodImage] 사용자: {}, 후보 {}개", userId, candidates.size());

        // AI 이름(AI Hub)으로 음식 검색을 그대로 씀 → 이름이 똑같은 음식이 맨 앞, 그다음 가정식·외식 순
        return FoodImageAnalysisResponse.of(candidates.stream()
                .map(c -> new FoodImageAnalysisResponse.Candidate(c.name(), c.confidence(),
                        foodService.search(c.name(), 0, FOODS_PER_CANDIDATE).items()))
                .toList());
    }

    // AI 응답을 그대로 믿지 않음: 이름이 빈 후보는 빼고, 확신도 높은 순으로 다시 세운 뒤 3개까지만
    // (명세상 AI가 정렬된 3개를 주지만, AI 서버가 바뀌어도 500이나 엉뚱한 판단이 나지 않게)
    private List<FoodImageClient.Candidate> cleanUp(FoodImageClient.Result result) {
        if (result == null || result.candidates() == null) {
            return List.of();
        }
        return result.candidates().stream()
                .filter(c -> c != null && c.name() != null && !c.name().isBlank())
                .sorted(Comparator.comparingDouble(FoodImageClient.Candidate::confidence).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
