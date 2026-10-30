package com.dangdang.global.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("front")
public record FrontProperties(String baseUrl) {
}
