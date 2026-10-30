package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.request.LoginRequest;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptStore loginAttemptStore;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = User.normalize(request.email());
        if (loginAttemptStore.isLocked(email)) { // ① 잠겨 있으면 비밀번호 비교도 안 한다
            throw new CustomException(AuthErrorCode.LOGIN_LOCKED);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.getAuthProvider() == AuthProvider.KAKAO) { // ② AUTH-05
            throw new CustomException(AuthErrorCode.KAKAO_ACCOUNT);
        }
        // ③ 없는 이메일과 틀린 비밀번호를 같은 문구로 → 가입 여부를 알려 주지 않는다
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            long fails = loginAttemptStore.increaseFails(email);
            log.info("[Auth] 로그인 실패 - 이메일: {}, 실패: {}회", email, fails);
            if (fails >= LoginAttemptStore.MAX_FAILS) {
                throw new CustomException(AuthErrorCode.LOGIN_LOCKED);
            }
            long left = LoginAttemptStore.MAX_FAILS - fails;
            throw new CustomException(
                    AuthErrorCode.LOGIN_FAILED,
                    AuthErrorCode.LOGIN_FAILED.getMessage() + " (남은 횟수 " + left + "번)");
        }
        loginAttemptStore.clear(email); // ④ 성공하면 실패 횟수 0
        log.info("[Auth] 로그인 - 사용자: {}", user.getId());
        return tokenService.issue(AuthPrincipal.from(user));
    }
}
