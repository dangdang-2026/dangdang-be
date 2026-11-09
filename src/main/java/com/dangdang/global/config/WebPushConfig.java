package com.dangdang.global.config;

import com.dangdang.global.config.property.VapidProperties;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.Security;

// 진짜 발송을 쓸 때만 켜짐.
@Configuration
@ConditionalOnProperty(name = "push.sender", havingValue = "webpush")
public class WebPushConfig {

    @Bean
    public PushService pushService(VapidProperties vapid) throws GeneralSecurityException {
        if (!StringUtils.hasText(vapid.publicKey()) || !StringUtils.hasText(vapid.privateKey())
                || !StringUtils.hasText(vapid.subject())) {
            throw new IllegalStateException("VAPID_PUBLIC_KEY·VAPID_PRIVATE_KEY·VAPID_SUBJECT 값이 비어 있어요.");
        }
        Security.addProvider(new BouncyCastleProvider());   // web-push가 쓰는 암호 도구 등록 (두 번 불러도 괜찮다)
        return new PushService(vapid.publicKey(), vapid.privateKey(), vapid.subject());
    }
}
