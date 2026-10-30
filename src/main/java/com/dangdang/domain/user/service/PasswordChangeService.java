package com.dangdang.domain.user.service;

import com.dangdang.domain.auth.dto.response.TokenResponse;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.RefreshTokenStore;
import com.dangdang.domain.auth.service.TokenService;
import com.dangdang.domain.user.dto.request.PasswordChangeRequest;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.AuthProvider;
import com.dangdang.domain.user.exception.UserErrorCode;
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
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenService tokenService;

    // 마이 › 비밀번호 바꾸기 (MY-06, M-09). 다른 기기는 로그아웃, 지금 기기는 새 토큰
    @Transactional
    public TokenResponse change(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.getAuthProvider() != AuthProvider.EMAIL) {
            throw new CustomException(UserErrorCode.KAKAO_NO_PASSWORD);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CustomException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.SAME_AS_OLD_PASSWORD);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenStore.deleteAll(userId);
        log.info("[User] 비밀번호 변경 - 사용자: {}", userId);
        return tokenService.issue(AuthPrincipal.from(user));
    }
}
