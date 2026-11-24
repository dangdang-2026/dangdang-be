package com.dangdang.domain.auth.service;

import com.dangdang.domain.auth.dto.response.AuthRetryInfo;
import com.dangdang.domain.auth.dto.response.EmailCodeSendResponse;
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

    public EmailCodeSendResponse sendCode(String rawEmail) {
        String email = User.normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(AuthErrorCode.EMAIL_ALREADY_USED);
        }
        long wait = store.startResendWait(email);   // 하루 횟수보다 먼저 → 대기 중 누른 건 안 셈
        if (wait > 0) {
            throw new CustomException(AuthErrorCode.RESEND_TOO_SOON, AuthErrorCode.RESEND_TOO_SOON.getMessage(),
                    AuthRetryInfo.waitSeconds(wait));                         // V-08
        }
        long sentToday = store.increaseSendCount(email);
        if (sentToday > EmailVerificationStore.MAX_SENDS_PER_DAY) {
            throw new CustomException(AuthErrorCode.EMAIL_SEND_LIMIT);   // V-11
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000)); // 앞자리 0 유지
        store.saveCode(email, code);
        try {
            emailSender.sendVerificationCode(email, code);
        } catch (CustomException e) {   // MAIL_SEND_FAILED → V-12
            store.clearResendWait(email);   // 바로 다시 누를 수 있게
            store.giveBackSend(email);      // 못 보낸 건 오늘 횟수에서 뺌
            throw e;
        }
        log.info("[Auth] 인증번호 발송 - 이메일: {}", email);
        return EmailCodeSendResponse.of(sentToday);
    }

    public void verify(String rawEmail, String code) {
        String email = User.normalize(rawEmail);
        String saved = store.findCode(email);
        if (saved == null) {
            if (store.isVerified(email)) {
                return;   // V-13 자동 확인이 두 번 와도 두 번째도 성공 (이미 인증됨)
            }
            if (store.tries(email) >= EmailVerificationStore.MAX_TRIES) {
                throw new CustomException(AuthErrorCode.EMAIL_CODE_TOO_MANY_TRIES);   // V-07
            }
            throw new CustomException(AuthErrorCode.EMAIL_CODE_EXPIRED);              // V-06
        }
        if (!saved.equals(code)) {
            int left = (int) (EmailVerificationStore.MAX_TRIES - store.increaseTries(email));
            if (left <= 0) {
                throw new CustomException(AuthErrorCode.EMAIL_CODE_TOO_MANY_TRIES);   // 5번째 → V-07
            }
            throw new CustomException(
                    AuthErrorCode.EMAIL_CODE_MISMATCH,
                    AuthErrorCode.EMAIL_CODE_MISMATCH.getMessage() + " " + left + "번 더 틀리면 인증번호를 다시 받아야 해요.",
                    AuthRetryInfo.attemptsLeft(left));                                // V-04
        }
        store.markVerified(email);
        log.info("[Auth] 이메일 인증 완료 - 이메일: {}", email);
    }
}
