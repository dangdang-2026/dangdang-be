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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final String DEFAULT_NAME = "당당이";   //닉네임 동의를 안 했을 때
    private static final String EMAIL_UNIQUE = "ux_users_email";   // V1 users 이메일 UNIQUE 인덱스

    private final KakaoCodeReader kakaoCodeReader;
    private final KakaoPendingStore pendingStore;
    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final TokenService tokenService;
    private final Clock clock;

    public enum KakaoLoginResult { LOGGED_IN, NEEDS_SIGNUP, EMAIL_ACCOUNT_EXISTS }

    // tokens는 LOGGED_IN일 때만, kakaoToken은 아닐 때만, email은 EMAIL_ACCOUNT_EXISTS일 때만
    public record Result(KakaoLoginResult result, TokenResponse tokens, String kakaoToken, String email) {}

    // 트랜잭션 없음 — 카카오 호출(최대 십몇 초) 동안 DB 연결을 잡지 않는다. 이메일 채우기는 한 줄 UPDATE가 따로 커밋
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
        // 검사는 보기만 하고(peek) 한다 — 이메일이 없거나 같은 이메일 계정이 있어 막혀도 토큰이 남아
        // K-08 「이메일로 로그인 → 연결」(KakaoLinkService)을 같은 토큰으로 이어 갈 수 있다
        KakaoUser kakao = pendingStore.peek(kakaoToken)
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
        // 검사를 다 통과했으니 이제 쓴다(GETDEL). 같은 토큰으로 동시에 눌렀으면 하나만 꺼내고 나머지는 AUTH_40011
        if (pendingStore.take(kakaoToken).isEmpty()) {
            throw new CustomException(AuthErrorCode.KAKAO_PENDING_EXPIRED);
        }
        User user = User.ofKakao(kakao.kakaoId(), displayName(kakao.nickname()), kakao.email());
        try {
            userRepository.saveAndFlush(user);   // 그 사이 같은 카카오·같은 이메일로 누가 가입했으면 DB UNIQUE가 막음
        } catch (DataIntegrityViolationException e) {
            // 어느 UNIQUE에 걸렸는지로 나눈다 — 실패한 flush 뒤라 여기서 DB를 다시 읽지 않는다
            throw new CustomException(violates(e, EMAIL_UNIQUE)
                    ? AuthErrorCode.EMAIL_ACCOUNT_EXISTS : AuthErrorCode.KAKAO_ALREADY_LINKED);
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
        try {
            if (userRepository.fillKakaoEmailIfFree(user.getId(), User.normalize(kakao.email()), clock.instant()) == 0) {
                log.warn("[Auth] 카카오 이메일 채우기 건너뜀 - 다른 계정이 사용 중, 사용자: {}", user.getId());
            }
        } catch (DataIntegrityViolationException e) {   // 바로 그 순간 누가 같은 이메일로 가입함 → 안 채우고 로그인은 그대로
            log.warn("[Auth] 카카오 이메일 채우기 건너뜀 - 동시에 같은 이메일 가입, 사용자: {}", user.getId());
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException c && constraint.equalsIgnoreCase(c.getConstraintName())) {
                return true;
            }
        }
        return false;
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
