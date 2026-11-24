package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.request.LoginRequest;
import com.dangdang.domain.auth.dto.response.AuthRetryInfo;
import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.LoginAttemptStore;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import com.dangdang.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    // 아무 의미 없는 비밀번호의 BCrypt 해시 (시간 맞추기용, cost 10 = 실제 해시와 같음)
    private static final String DUMMY_HASH = "$2a$10$fWxnuU5WdS24oKG81.PYDuzu41ZmtS1jMYGqpi7JEocTzDZk6RLbi";
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptStore loginAttemptStore;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = User.normalize(request.email());
        long lockedFor = loginAttemptStore.lockSecondsLeft(email);
        if (lockedFor > 0) { // ① 잠겨 있으면 비밀번호 비교도 안 한다
            throw locked(lockedFor);
        }
        // ② 비교하기 전에 먼저 센다 → 동시에 많이 보내도 비교는 5번까지만 (리뷰: 세고 나서 비교)
        long attempt = loginAttemptStore.countAttempt(email);
        // 세는 사이 다른 요청이 잠갔으면(lock이 LOCK을 먼저 쓰고 FAILS를 지움) 비교하지 않는다 (리뷰 #126)
        // 이 요청이 잠금 뒤 새로 센 FAILS는 지워서, 잠금이 풀린 뒤 남은 횟수로 다시 잠기지 않게
        long lockedNow = loginAttemptStore.lockSecondsLeft(email);
        if (lockedNow > 0) {
            loginAttemptStore.clear(email);
            throw locked(lockedNow);
        }
        if (attempt > LoginAttemptStore.MAX_FAILS) {   // 동시에 몰려 5번을 넘긴 나머지 요청
            loginAttemptStore.lock(email);
            throw locked(LoginAttemptStore.LOCK_TTL.toSeconds());
        }
        User user = userRepository.findByEmail(email).orElse(null);
        // ③ 없는 이메일·카카오 계정·틀린 비밀번호를 같은 문구·같은 횟수로 → 가입 여부를 알려 주지 않는다 (L-07)
        if (!passwordMatches(user, request.password())) {
            log.info("[Auth] 로그인 실패 - 이메일: {}, 실패: {}회", email, attempt);
            if (attempt >= LoginAttemptStore.MAX_FAILS) {
                loginAttemptStore.lock(email);
                throw locked(LoginAttemptStore.LOCK_TTL.toSeconds());
            }
            int left = (int) (LoginAttemptStore.MAX_FAILS - attempt);
            throw new CustomException(
                    AuthErrorCode.LOGIN_FAILED,
                    AuthErrorCode.LOGIN_FAILED.getMessage() + " " + left + "번 더 틀리면 5분 동안 잠겨요.",   // L-05 최종 문구
                    AuthRetryInfo.attemptsLeft(left));
        }
        loginAttemptStore.clear(email); // ④ 성공하면 센 횟수 0
        log.info("[Auth] 로그인 - 사용자: {}", user.getId());
        return tokenService.issue(AuthPrincipal.from(user));
    }

    // 비밀번호가 맞는지. 없는 이메일·카카오 계정·72바이트 넘는 입력은 늘 false
    // 그래도 BCrypt는 한 번 돌린다 → 응답 시간으로 가입 여부를 알 수 없게
    private boolean passwordMatches(User user, String raw) {
        if (user == null || user.getAuthProvider() != AuthProvider.EMAIL || user.getPasswordHash() == null
                || raw.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {   // BCrypt 한계 (76 D)
            passwordEncoder.matches("not-a-password", DUMMY_HASH);   // raw를 넣으면 72바이트 넘을 때 500 → 고정 글자로
            return false;
        }
        return passwordEncoder.matches(raw, user.getPasswordHash());
    }

    private static CustomException locked(long seconds) {
        return new CustomException(AuthErrorCode.LOGIN_LOCKED, AuthErrorCode.LOGIN_LOCKED.getMessage(),
                AuthRetryInfo.waitSeconds(seconds));
    }
}
