package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.KakaoPendingStore;
import com.dangdang.domain.auth.infra.kakao.KakaoCodeReader;
import com.dangdang.domain.auth.infra.kakao.KakaoUser;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final String DEFAULT_NAME = "당당이";   //닉네임 동의를 안 했을 때

    private final KakaoCodeReader kakaoCodeReader;
    private final KakaoPendingStore pendingStore;
    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final TokenService tokenService;

    public enum KakaoLoginResult { LOGGED_IN, NEEDS_SIGNUP, EMAIL_ACCOUNT_EXISTS }

    // tokens는 LOGGED_IN일 때만, kakaoToken은 아닐 때만, email은 EMAIL_ACCOUNT_EXISTS일 때만
    public record Result(KakaoLoginResult result, TokenResponse tokens, String kakaoToken, String email) {}

    @Transactional   // 예전: readOnly = true → 이제 빈 이메일을 채울 수 있어서
    public Result login(String code, String redirectUri) {
        //1) 허용한 주소인지 보고 카카오에 물어보기(코드 → 토큰 → 사용자)
        KakaoUser kakao = kakaoCodeReader.read(code, redirectUri);

        //2) 이 카카오가 붙은 계정(카카오 가입 또는 연결)이 있으면 바로 로그인
        User user = userRepository.findByProviderSubject(kakao.kakaoId()).orElse(null);
        if (user != null) {
            fillEmailIfEmpty(user, kakao);
            log.info("[Auth] 카카오 로그인 - 사용자: {}", user.getId());
            return new Result(KakaoLoginResult.LOGGED_IN, tokenService.issue(AuthPrincipal.from(user)), null, null);
        }

        //3) 처음 보는 카카오 → 아직 만들지 않는다. 가입할지·이메일 계정에 붙일지 고르게 토큰만 (K-07 · K-08)
        String token = pendingStore.save(kakao);
        if (kakao.email() != null && userRepository.existsByEmail(User.normalize(kakao.email()))) {
            log.info("[Auth] 카카오 - 같은 이메일 계정 있음 (K-07)");
            return new Result(KakaoLoginResult.EMAIL_ACCOUNT_EXISTS, null, token, User.normalize(kakao.email()));
        }
        return new Result(KakaoLoginResult.NEEDS_SIGNUP, null, token, null);
    }

    // K-08 「처음이에요, 가입할게요」
    @Transactional
    public TokenResponse signup(String kakaoToken) {
        KakaoUser kakao = pendingStore.take(kakaoToken)
                .orElseThrow(() -> new CustomException(AuthErrorCode.KAKAO_PENDING_EXPIRED));
        // 인증된 이메일이 없으면 새 계정을 만들지 않는다 — 나중에 같은 이메일로 이메일 가입하면 두 계정이 된다
        // (이메일 계정에 연결하는 건 KakaoLinkService라 여기와 상관없이 된다)
        if (kakao.email() == null) {
            throw new CustomException(AuthErrorCode.KAKAO_EMAIL_REQUIRED);
        }
        // 같은 이메일 계정이 있으면 합치지 않는다 (AUTH-05) — 그 사이 누가 그 이메일로 가입했어도 여기서 막힘
        if (userRepository.existsByEmail(User.normalize(kakao.email()))) {
            throw new CustomException(AuthErrorCode.EMAIL_ACCOUNT_EXISTS);
        }
        User user = User.ofKakao(kakao.kakaoId(), displayName(kakao.nickname()), kakao.email());
        try {
            userRepository.saveAndFlush(user);   // 같은 카카오가 이미 다른 계정에 붙었으면 DB UNIQUE가 막음
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorCode.KAKAO_ALREADY_LINKED);
        }
        notificationPreferenceRepository.save(NotificationPreference.defaultsFor(user));
        log.info("[Auth] 카카오 가입 - 사용자: {}", user.getId());
        return tokenService.issue(AuthPrincipal.from(user));
    }

    // 이메일 필수 전에 가입해 이메일이 빈 카카오 계정 → 이번에 카카오가 준 인증된 이메일로 채운다
    // 다른 계정이 이미 그 이메일을 쓰면 채우지 않는다 (합치지 않는다, AUTH-05) — 로그인은 그대로
    private void fillEmailIfEmpty(User user, KakaoUser kakao) {
        if (user.getEmail() != null || kakao.email() == null) {
            return;
        }
        String email = User.normalize(kakao.email());
        if (userRepository.existsByEmail(email)) {
            log.warn("[Auth] 카카오 이메일 채우기 건너뜀 - 다른 계정이 사용 중, 사용자: {}", user.getId());
            return;
        }
        user.fillKakaoEmail(email);   // 트랜잭션 끝날 때 저장 (더티 체킹)
    }

    //display_name 칸이 10글자 제한.
    private String displayName(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NAME;
        }
        String trimmed = nickname.trim();
        return trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed;
    }
}
