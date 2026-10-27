package com.dangdang.domain.auth.dto.response;

import com.dangdang.domain.user.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmailAvailabilityResponse(
        @Schema(description = "가입에 쓸 수 있으면 true", example ="false") boolean available,
        @Schema(description = "이미 가입돼 잇으면 가입 방법 (EMAIL → J-06, KAKAO → J-07). 쓸 수 있으면 null", example = "KAKAO")
        AuthProvider authProvider){

    public static EmailAvailabilityResponse ok(){
        return new EmailAvailabilityResponse(true, null);
    }
    public static EmailAvailabilityResponse usedBy(AuthProvider provider){
        return new EmailAvailabilityResponse(false, provider);
    }
}
