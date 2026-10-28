package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.EmailSender;
import com.dangdang.domain.auth.infra.LoginAttemptStore;
import com.dangdang.domain.auth.infra.PasswordResetStore;
import com.dangdang.domain.auth.infra.RefreshTokenStore;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.config.property.FrontProperties;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetStore resetStore;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptStore loginAttemptStore;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final FrontProperties frontProperties;

    // 1) 링크 보내기. 가입 여부와 관계없이 똑같이 끝낸다 (R-04: 계정이 있는지 알려 주지 않음)
    @Transactional(readOnly = true)
    public void sendLink(String rawEmail) {
        String email = User.normalize(rawEmail);
        if (resetStore.increaseSendCount(email) > PasswordResetStore.MAX_SENDS_PER_DAY) {
            throw new CustomException(AuthErrorCode.RESET_SEND_LIMIT); // 없는 이메일도 똑같이 셈
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getAuthProvider() != AuthProvider.EMAIL) {
            log.info("[Auth] 재설정 요청 - 보낼 계정 없음: {}", email); // 카카오 계정은 비밀번호가 없음
            return;
        }
        String token = newToken();
        resetStore.save(user.getId(), token);
        emailSender.sendPasswordResetLink(email, frontProperties.baseUrl() + "/reset-password?token=" + token);
        log.info("[Auth] 재설정 링크 발송 - 사용자: {}", user.getId());
    }

    // 2) 새 비밀번호 저장. 성공하면 링크 끝 + 모든 기기 로그아웃
    @Transactional
    public void confirm(String token, String newPassword) {
        Long userId = resetStore.findUserId(token);
        if (userId == null) {
            throw new CustomException(AuthErrorCode.RESET_LINK_EXPIRED); // R-09
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.RESET_LINK_EXPIRED)); // 그 사이 탈퇴
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.SAME_AS_OLD_PASSWORD); // R-07. 링크는 그대로 -> 다시 입력 가능
        }
        if (!resetStore.consume(token, userId)) {
            throw new CustomException(AuthErrorCode.RESET_LINK_EXPIRED); // 동시에 두 번 -> 하나만
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.deleteAll(userId);
        loginAttemptStore.unlock(user.getEmail());
        log.info("[Auth] 비밀번호 재설정 - 사용자: {}", userId);
    }

    // 32바이트 무작위 -> 주소에 넣어도 안전한 글자 (43자)
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
