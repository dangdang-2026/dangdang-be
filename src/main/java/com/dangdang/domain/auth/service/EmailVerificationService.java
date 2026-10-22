package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.exception.AuthErrorCode;
import com.dangdang.domain.auth.infra.EmailSender;
import com.dangdang.domain.auth.infra.EmailVerificationStore;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.repository.UserRepository;
import com.dangdang.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationStore store;
    private final EmailSender emailSender;
    private final UserRepository userRepository;

    public void sendCode(String rawEmail) {
        String email = User.normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(AuthErrorCode.EMAIL_ALREADY_USED);
        }
        if (store.increaseSendCount(email) > EmailVerificationStore.MAX_SENDS_PER_DAY) {
            throw new CustomException(AuthErrorCode.EMAIL_SEND_LIMIT);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000)); // 앞자리 0 유지
        store.saveCode(email, code);
        emailSender.sendVerificationCode(email, code);
        log.info("[Auth] 인증번호 발송 - 이메일: {}", email);
    }

    public void verify(String rawEmail, String code) {
        String email = User.normalize(rawEmail);
        String saved = store.findCode(email);
        if (saved == null) {
            throw new CustomException(AuthErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!saved.equals(code)) {
            store.increaseTries(email);
            throw new CustomException(AuthErrorCode.EMAIL_CODE_MISMATCH);
        }
        store.markVerified(email);
        log.info("[Auth] 이메일 인증 완료 - 이메일: {}", email);
    }
}
