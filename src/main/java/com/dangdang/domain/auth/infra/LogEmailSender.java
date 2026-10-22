package com.dangdang.domain.auth.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogEmailSender implements  EmailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("[인증번호] {} → {}", email, code);
    }
}
