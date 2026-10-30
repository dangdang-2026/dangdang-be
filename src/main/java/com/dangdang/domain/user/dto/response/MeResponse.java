package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeResponse(
        @Schema(description = "회원 번호", example = "1")Long userId,
        @Schema(description = "이메일(카카오는 없을 수 있음)", example = "jisu@example.com") String email,
        @Schema(description = "부를 이름", example = "지수") String displayName,
        @Schema(description = "가입 방식", example = "EMAIL") AuthProvider authProvider,
        @Schema(description = "필수 동의를 마쳤는지 (false면 A-01로)", example = "true") boolean consentCompleted) {

    public static MeResponse from(User user, boolean consentCompleted)  {
        return new MeResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getAuthProvider(), consentCompleted);
    }
}
