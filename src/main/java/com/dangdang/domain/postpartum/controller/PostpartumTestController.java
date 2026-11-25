package com.dangdang.domain.postpartum.controller;

import com.dangdang.domain.postpartum.dto.request.PostpartumResultRequest;
import com.dangdang.domain.postpartum.dto.request.PostpartumTestCreateRequest;
import com.dangdang.domain.postpartum.dto.request.PostpartumTestUpdateRequest;
import com.dangdang.domain.postpartum.dto.response.PostpartumTestResponse;
import com.dangdang.domain.postpartum.service.PostpartumTestService;
import com.dangdang.global.common.BaseResponse;
import com.dangdang.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Postpartum Test", description = "산후 검사 일정 API")
@RestController
@RequestMapping("/api/postpartum/tests")
@RequiredArgsConstructor
public class PostpartumTestController {

    private final PostpartumTestService postpartumTestService;

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 검사 일정 등록 ]",
            description = """
                    **Parameters** name(40자), testDate, placeName(검사 장소 50자, 없어도 됨), remindDayBefore·remindSameDay(안 보내면 true)
                    **Returns** 저장한 일정(placeName 포함). 산후(출산일 저장)가 아니면 409 POSTPARTUM_40901
                    알림(한국 시간) — 전날 저녁 7~10시 「내일 ○○ 검사가 있어요. 병원에서 들은 준비 사항(금식 등)도 확인해 주세요」(10시 지나 등록하면 전날 알림은 건너뜀),
                    당일 아침 7시~밤 10시 「오늘 ○○ 검사가 있어요」(지난 날짜로 적은 검사는 알림 없음). 이름이 「검사」로 끝나면 「검사」를 또 붙이지 않는다. 장소는 알림에 안 넣는다.
                    알림함에는 「오늘·내일」 대신 날짜로 남는다 (「11월 10일(화) ○○ 검사가 있어요」).
                    진료·검사 알림을 끄면 안 가고, 산후가 아닌 동안(출산 되돌리기 등)에도 안 간다
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<PostpartumTestResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody PostpartumTestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "산후 검사 일정을 저장했습니다.",
                postpartumTestService.create(principal.userId(), request)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 검사 목록 ]", description = "**Returns** 날짜 순 (지난 검사 포함)")
    @GetMapping
    public ResponseEntity<BaseResponse<List<PostpartumTestResponse>>> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "산후 검사 목록 조회에 성공했습니다.",
                postpartumTestService.list(principal.userId())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 검사 일정 수정 ]",
            description = "**Parameters** 바꿀 칸만 (안 보낸 칸은 그대로, placeName은 빈 글이면 지움). 날짜를 바꾸면 알림을 다시 보낸다. 남의 것·없는 번호 404 POSTPARTUM_40401")
    @PatchMapping("/{testId}")
    public ResponseEntity<BaseResponse<PostpartumTestResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long testId,
            @Valid @RequestBody PostpartumTestUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "산후 검사 일정을 고쳤습니다.",
                postpartumTestService.update(principal.userId(), testId, request)));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 검사 결과 메모 ]",
            description = "**Parameters** resultNote(500자, 비우면 지움) — 들은 그대로. 해석·다음 시기 계산 없음 (PP-05)")
    @PatchMapping("/{testId}/result")
    public ResponseEntity<BaseResponse<PostpartumTestResponse>> writeResult(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long testId,
            @Valid @RequestBody PostpartumResultRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "결과 메모를 저장했습니다.",
                postpartumTestService.writeResult(principal.userId(), testId, request.resultNote())));
    }

    @Operation(summary = "[ 사용자 | 토큰 O | 산후 검사 일정 지우기 ]")
    @DeleteMapping("/{testId}")
    public ResponseEntity<BaseResponse<Void>> delete(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long testId) {
        postpartumTestService.delete(principal.userId(), testId);
        return ResponseEntity.ok(BaseResponse.success(200, "산후 검사 일정을 지웠습니다.", null));
    }
}
