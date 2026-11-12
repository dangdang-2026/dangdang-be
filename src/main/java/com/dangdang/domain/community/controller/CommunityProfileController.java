package com.dangdang.domain.community.controller;

import com.dangdang.domain.community.dto.request.CommunityProfileCreateRequest;
import com.dangdang.domain.community.dto.request.CommunityProfileUpdateRequest;
import com.dangdang.domain.community.dto.response.CommunityProfileResponse;
import com.dangdang.domain.community.dto.response.NicknameAvailabilityResponse;
import com.dangdang.domain.community.dto.response.NicknameSuggestionResponse;
import com.dangdang.domain.community.service.CommunityProfileService;
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

@Tag(name = "Community Profile", description = "커뮤니티 프로필 API")
@RestController
@RequestMapping("/api/community/profiles")
@RequiredArgsConstructor
public class CommunityProfileController {

    private final CommunityProfileService profileService;

    @Operation(
            summary = "[ 사용자 | 토큰 O | 닉네임 중복 확인 ]",
            description =
                    """
                    **Parameters** nickname(2~10자, 한글·영문·숫자)
                    **Returns** available. 형식이 틀리면 COMMUNITY_40001
                    """)
    @GetMapping("/nickname-availability")
    public ResponseEntity<BaseResponse<NicknameAvailabilityResponse>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(BaseResponse.success(200, "커뮤니티 닉네임 중복 확인에 성공했습니다.",
                profileService.checkNickname(nickname)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 랜덤 닉네임 추천 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** nickname. 지금 아무도 안 쓰는 이름 (예: 포근한 고구마). 저장은 안 함 → 마음에 들면 POST로 생성.
                    그 사이 남이 가져가면 생성이 COMMUNITY_40901, 추천할 이름을 못 찾으면 COMMUNITY_40903
                    """)
    @GetMapping("/nickname-suggestion")
    public ResponseEntity<BaseResponse<NicknameSuggestionResponse>> suggestNickname() {
        return ResponseEntity.ok(BaseResponse.success(200, "랜덤 닉네임 추천에 성공했습니다.",
                profileService.suggestNickname()));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 커뮤니티 프로필 생성 ]",
            description =
                    """
                    **Parameters** nickname, showWeekBadge(선택, 기본 false)
                    **Returns** id, nickname, showWeekBadge. 이름 중복 COMMUNITY_40901, 프로필 있음 COMMUNITY_40902
                    """)
    @PostMapping
    public ResponseEntity<BaseResponse<CommunityProfileResponse>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CommunityProfileCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(201, "커뮤니티 프로필 생성에 성공했습니다.",
                profileService.create(principal.userId(), request)));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 내 커뮤니티 프로필 ]",
            description =
                    """
                    **Parameters** 없음
                    **Returns** id, nickname, showWeekBadge. 없으면 COMMUNITY_40401
                    """)
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<CommunityProfileResponse>> getMine(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(BaseResponse.success(200, "내 커뮤니티 프로필 조회에 성공했습니다.",
                profileService.getMine(principal.userId())));
    }

    @Operation(
            summary = "[ 사용자 | 토큰 O | 커뮤니티 프로필 수정 ]",
            description =
                    """
                    **Parameters** nickname(선택), showWeekBadge(선택). 하나는 꼭 보내기
                    **Returns** 바뀐 프로필. 둘 다 없으면 COMMUNITY_40002, 이름 중복 COMMUNITY_40901
                    """)
    @PatchMapping("/me")
    public ResponseEntity<BaseResponse<CommunityProfileResponse>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody CommunityProfileUpdateRequest request) {
        return ResponseEntity.ok(BaseResponse.success(200, "커뮤니티 프로필 수정에 성공했습니다.",
                profileService.update(principal.userId(), request)));
    }
}
