package com.dangdang.domain.announcement.controller;

import com.dangdang.domain.announcement.dto.request.AnnouncementCreateRequest;
import com.dangdang.domain.announcement.dto.response.AnnouncementResponse;
import com.dangdang.domain.announcement.enums.AnnouncementSlot;
import com.dangdang.domain.announcement.service.AnnouncementService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Announcement", description = "스플래시·커뮤니티 소식/광고 카드 API")
@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "[ 사용자 | 토큰 O | 지금 걸린 소식·광고 카드 (S-01b·S-01c · U-01) ]",
            description = """
                    **Parameters** slot(SPLASH 재실행 스플래시 · COMMUNITY 커뮤니티 메인 1칸)
                    **Returns** 당당 소식(NEWS)이 먼저, 그다음 제휴(AD — 화면에 「AD」 표시). 없으면 []
                    하루 1번·최대 1.2초 보여 주기는 프론트가 정한다
                    """)
    @GetMapping("/api/announcements")
    public ResponseEntity<BaseResponse<List<AnnouncementResponse>>> active(@RequestParam AnnouncementSlot slot) {
        return ResponseEntity.ok(BaseResponse.success(200, "소식 카드 조회에 성공했습니다.", announcementService.active(slot)));
    }

    @Operation(summary = "[ 운영자 | 소식·광고 카드 걸기 ]",
            description = "광고(AD)는 reviewedBy 필수 (AD-03 심사 기준: 혈당 개선 표방 식품·당 함유 간식·의료기기·보험 제외). "
                    + "linkUrl은 /로 시작하는 앱 경로나 https만. contentCardId는 올라가 있는 카드만 (아니면 409 ANNOUNCEMENT_40901)")
    @PostMapping("/api/admin/announcements")
    public ResponseEntity<BaseResponse<AnnouncementResponse>> create(@Valid @RequestBody AnnouncementCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "카드를 걸었습니다.",
                announcementService.create(request)));
    }

    @Operation(summary = "[ 운영자 | 소식·광고 카드 내리기 ]", description = "없는 id면 404 ANNOUNCEMENT_40401")
    @PostMapping("/api/admin/announcements/{id}/end")
    public ResponseEntity<BaseResponse<AnnouncementResponse>> end(@PathVariable Long id) {
        return ResponseEntity.ok(BaseResponse.success(200, "카드를 내렸습니다.", announcementService.end(id)));
    }
}
