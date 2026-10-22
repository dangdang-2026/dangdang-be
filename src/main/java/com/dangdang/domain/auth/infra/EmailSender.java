package com.dangdang.domain.auth.infra;

public interface EmailSender {
    void sendVerificationCode(String email, String code);
}
