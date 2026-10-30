package com.dangdang.domain.auth.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "mail.sender", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements  EmailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("[인증번호] {} → {}", email, code);
    }

    @Override
    public void sendPasswordResetLink(String email, String link) {log.info("[비밀번호 재설정] {} - {}", email, link);}
}
