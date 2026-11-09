package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vapid")
public record VapidProperties(String publicKey, String privateKey, String subject) {
}
