package com.dangdang.domain.user.dto.response;

import com.dangdang.domain.pregnancy.entity.Pregnancy;
import com.dangdang.domain.pregnancy.enums.PregnancyState;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record MeResponse(
        @Schema(description = "회원 번호", example = "1")Long userId,
        @Schema(description = "이메일(카카오는 없을 수 있음)", example = "jisu@example.com") String email,
        @Schema(description = "부를 이름", example = "지수") String displayName,
        @Schema(description = "가입 방식", example = "EMAIL") AuthProvider authProvider,
        @Schema(description = "필수 동의를 지금 문서 버전으로 마쳤는지 (false면 A-01로 — 건강정보 철회·문서 버전 올림 포함)", example = "true") boolean consentCompleted,
        @Schema(description = "이름 바꿀 때 그대로 보낼 수정 버전 (BIGINT 문자열)", example = "0") String version,
        @Schema(description = "출산 예정일 (안 적었으면 null)", example = "2026-12-20") LocalDate dueDate,
        @Schema(description = "임신 상태 PREGNANT·HOSPITALIZED·PAUSED(다른 상황 · 주수 숨김)·POSTPARTUM (임신 정보가 없으면 null)", example = "PREGNANT")
        PregnancyState pregnancyState,
        @Schema(description = "오늘 임신 주수 (예정일이 없거나 다른 상황(PAUSED)이면 null)") PregnancyWeek pregnancyWeek,
        @Schema(description = "로그인 방식 (M-02 「로그인 방식」)") LoginMethods loginMethods) {

    // 이메일: 비밀번호가 있으면(이메일로 가입) · 카카오: 카카오로 가입했거나 연결했으면
    public record LoginMethods(
            @Schema(description = "이메일·비밀번호로 로그인할 수 있는지 → 「비밀번호 바꾸기」", example = "true") boolean email,
            @Schema(description = "카카오로 로그인할 수 있는지 → false면 「연결하기」", example = "false") boolean kakao) {
    }

    // 「임신 26주 3일」 — 예정일 기준 280일(40주)에서 거꾸로 센다 (기능명세서 MY-01)
    public record PregnancyWeek(
            @Schema(example = "26") int weeks,
            @Schema(example = "3") int days) {

        static PregnancyWeek of(LocalDate dueDate, LocalDate today) {
            if (dueDate == null) {
                return null;
            }
            long elapsed = 280 - ChronoUnit.DAYS.between(today, dueDate);
            if (elapsed < 0) {
                return null;   // 예정일이 280일보다 더 뒤 — 잘못 적은 날짜라 주수를 보여 주지 않는다
            }
            return new PregnancyWeek((int) (elapsed / 7), (int) (elapsed % 7));
        }
    }

    public static MeResponse from(User user, boolean consentCompleted, Pregnancy pregnancy, LocalDate today) {
        LocalDate dueDate = pregnancy == null ? null : pregnancy.getDueDate();
        // 다른 상황(PAUSED)이면 주수를 서버에서도 주지 않는다 — 상황을 짐작할 수 없게 (E-03b 「임신 주수 숨김」)
        boolean paused = pregnancy != null && pregnancy.getState() == PregnancyState.PAUSED;
        return new MeResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getAuthProvider(), consentCompleted,
                String.valueOf(user.getRowVersion()),
                dueDate,
                pregnancy == null ? null : pregnancy.getState(),
                paused ? null : PregnancyWeek.of(dueDate, today),
                new LoginMethods(user.hasPassword(), user.hasKakao()));
    }
}
