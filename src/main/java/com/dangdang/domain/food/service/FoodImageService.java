package com.dangdang.domain.food.service;

import com.dangdang.domain.food.dto.response.FoodImageAnalysisResponse;
import com.dangdang.domain.food.exception.FoodErrorCode;
import com.dangdang.domain.food.infra.FoodImageClient;
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
import java.util.List;

// 음식 사진 AI 분석: AI 서버에서 후보를 받고, 후보 이름을 식약처 음식(foods)과 맞춤
// DB에 쓰지 않아서 @Transactional 이 필요 없음. 사진은 저장하지도, 로그에 남기지도 않음
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodImageService {

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // AI 서버가 받는 최대 크기 10MB
    static final double MIN_CONFIDENCE = 0.7;                // 1등이 이보다 낮으면 「이름으로 적기」 (명세 2026-11-14 기준)
    private static final int FOODS_PER_CANDIDATE = 3;        // 후보 하나에 붙일 식약처 음식 수

    private final FoodImageClient foodImageClient;
    private final FoodService foodService;

    public FoodImageAnalysisResponse analyze(Long userId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new CustomException(FoodErrorCode.IMAGE_REQUIRED);
        }
        // multipart 설정(max-file-size)에서 먼저 막히지만, 설정이 바뀌어도 AI 서버 한도를 넘기지 않게 한 번 더 봄
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_IMAGE_BYTES);
        }

        FoodImageClient.Result result;
        try {
            result = foodImageClient.candidates(readBytes(image), image.getOriginalFilename(), image.getContentType());
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

        List<FoodImageClient.Candidate> candidates = result == null || result.candidates() == null
                ? List.of() : result.candidates();
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

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
