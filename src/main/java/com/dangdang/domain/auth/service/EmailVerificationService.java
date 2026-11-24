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
        String sendKey = store.todaySendKey(email);
        long sentToday = store.increaseSendCount(sendKey);
        if (sentToday > EmailVerificationStore.MAX_SENDS_PER_DAY) {
            store.clearResendWait(email);   // 오늘은 끝 → 「0:50 후 다시 받기」 타이머가 뜨지 않게
            throw new CustomException(AuthErrorCode.EMAIL_SEND_LIMIT);   // V-11
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000)); // 앞자리 0 유지
        try {
            emailSender.sendVerificationCode(email, code);
        } catch (CustomException e) {   // MAIL_SEND_FAILED → V-12
            store.clearResendWait(email);     // 바로 다시 누를 수 있게
            store.giveBackSend(sendKey);      // 못 보낸 건 오늘 횟수에서 뺌 (올린 날짜 키 그대로)
            throw e;
        }
        store.saveCode(email, code);   // 보낸 뒤에 저장 → 못 보냈으면 전에 받은 번호가 그대로 살아 있음
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
        long attempt = store.countTry(email);   // 비교하기 전에 먼저 센다
        if (attempt > EmailVerificationStore.MAX_TRIES) {   // 동시에 몰려 5번을 넘긴 나머지 요청
            store.dropCode(email);
            throw new CustomException(AuthErrorCode.EMAIL_CODE_TOO_MANY_TRIES);
        }
        if (!saved.equals(code)) {
            int left = (int) (EmailVerificationStore.MAX_TRIES - attempt);
            if (left <= 0) {
                store.dropCode(email);
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
