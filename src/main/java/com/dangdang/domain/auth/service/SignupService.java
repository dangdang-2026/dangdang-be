package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.request.SignupRequest;
import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.EmailVerificationStore;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final EmailVerificationStore verificationStore;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signup(SignupRequest request) {
        String email = User.normalize(request.email());
        if (!verificationStore.isVerified(email)) {
            throw new CustomException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        User user =
                User.ofEmail(
                        email,
                        request.displayName().trim(),
                        passwordEncoder.encode(request.password()),
                        Instant.now());
        try {
            userRepository.saveAndFlush(user); // 같은 이메일이 동시에 오면 여기서 DB UNIQUE 가 막음
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorCode.EMAIL_ALREADY_USED);
        }
        notificationPreferenceRepository.save(NotificationPreference.defaultsFor(user));
        verificationStore.clearVerified(email);
        log.info("[Auth] 회원가입 - 이메일: {}", email);
        return user.getId();
    }
}
