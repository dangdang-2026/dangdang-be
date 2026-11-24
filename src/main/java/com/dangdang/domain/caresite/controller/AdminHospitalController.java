package com.dangdang.domain.caresite.controller;

import com.dangdang.domain.caresite.repository.HospitalRepository;
import com.dangdang.domain.caresite.service.HospitalSyncService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** 는 SecurityConfig에서 ADMIN만 들어온다
@Tag(name = "Admin · Hospital", description = "운영자 병원 목록 적재 API")
@RestController
@RequestMapping("/api/admin/hospitals")
@RequiredArgsConstructor
public class AdminHospitalController {

    private final HospitalSyncService hospitalSyncService;
    private final HospitalRepository hospitalRepository;

    public record SyncStatus(long count, boolean running) {
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 병원 목록 지금 다시 받기 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** 202. 뒤에서 심평원 전체 목록(약 8만 곳)을 받기 시작함 (1시간 남짓). 이미 도는 중이면 시작하지 않고 running=true.
                    진행은 GET /api/admin/hospitals 의 count·running, 서버 로그 [HospitalSync]로 확인
                    """)
    @PostMapping("/sync")
    public ResponseEntity<BaseResponse<SyncStatus>> sync() {
        boolean started = hospitalSyncService.startInBackground();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.success(202, started ? "병원 목록 받기를 시작했습니다." : "이미 받는 중입니다.",
                        new SyncStatus(hospitalRepository.count(), true)));
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 병원 목록 상태 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** count(받아 둔 병원 수) · running(지금 받는 중인지)
                    """)
    @GetMapping
    public ResponseEntity<BaseResponse<SyncStatus>> status() {
        return ResponseEntity.ok(BaseResponse.success(200, "병원 목록 상태 조회에 성공했습니다.",
                new SyncStatus(hospitalRepository.count(), hospitalSyncService.isRunning())));
    }
}
