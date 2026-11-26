package com.dangdang.domain.admin.controller;

import com.dangdang.domain.admin.dto.response.AdminMemberDetailResponse;
import com.dangdang.domain.admin.dto.response.AdminMemberPageResponse;
import com.dangdang.domain.admin.enums.MemberListStatus;
import com.dangdang.domain.admin.service.AdminMemberQueryService;
import com.dangdang.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 운영자 회원 목록·상세. 읽기만 한다 (이용 제한은 POST /api/admin/sanctions)
@Tag(name = "Admin · Member", description = "운영자 회원 API")
@RestController
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberQueryService adminMemberQueryService;

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 회원 목록 ]",
            description =
                    """
                    **Parameters** status(ALL 기본 · RESTRICTED), q(닉네임·부를 이름 일부, 50자), page(0~10000), size(1~100, 기본 30)
                    **Returns** 탭 숫자(totalCount · restrictedCount) + 최근 가입 순 items. 이메일은 앞 두 글자만, 카카오 가입이면 null.
                    운영자 계정은 빠진다. 건강정보는 주지 않는다. 다음 쪽이 있으면 hasNext=true. ADMIN이 아니면 403
                    """)
    @GetMapping("/api/admin/members")
    public ResponseEntity<BaseResponse<AdminMemberPageResponse>> page(
            @RequestParam(defaultValue = "ALL") MemberListStatus status,
            @RequestParam(required = false) @Size(max = 50, message = "q는 50자까지예요.") String q,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0~10000이에요.")
            @Max(value = 10000, message = "page는 0~10000이에요.") int page,
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = "size는 1~100이에요.")
            @Max(value = 100, message = "size는 1~100이에요.") int size) {
        return ResponseEntity.ok(BaseResponse.success(200, "회원 목록 조회에 성공했습니다.",
                adminMemberQueryService.page(status, q, page, size)));
    }

    @Operation(
            summary = "[ 운영자 | 토큰 O(ADMIN) | 회원 상세 ]",
            description =
                    """
                    **Parameters** userId
                    **Returns** 기본 정보, 글·댓글·가려진 수·받은 신고 수, 지금 제한(activeSanction, 없으면 null), 제한 이력, 받은 신고 최근 20건.
                    건강정보는 주지 않는다. 없는 회원이나 운영자 계정이면 404 USER_40401. ADMIN이 아니면 403
                    """)
    @GetMapping("/api/admin/members/{userId}")
    public ResponseEntity<BaseResponse<AdminMemberDetailResponse>> detail(@PathVariable Long userId) {
        return ResponseEntity.ok(BaseResponse.success(200, "회원 상세 조회에 성공했습니다.",
                adminMemberQueryService.detail(userId)));
    }
}
