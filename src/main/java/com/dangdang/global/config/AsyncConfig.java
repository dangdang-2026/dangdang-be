package com.dangdang.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// @Async 를 켠다 (재설정 메일을 기다리지 않고 바로 응답)
@Configuration
@EnableAsync
public class AsyncConfig {
}