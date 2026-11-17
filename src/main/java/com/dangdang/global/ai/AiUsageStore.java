package com.dangdang.global.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

// 오늘(한국 날짜) 몇 번 썼는지 — Redis 키 ai:usage:CHAT:7:2026-11-14
@Component
@RequiredArgsConstructor
public class AiUsageStore {

    private static final String PREFIX = "ai:usage:";
    private final StringRedisTemplate redis;

    public long increase(AiFeature feature, Long userId, LocalDate day) {
        String key = key(feature, userId, day);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofDays(2));   // 다음 날이 지나면 저절로 지워진다
        }
        return count == null ? 0 : count;
    }

    // AI 서버가 실패했을 때 한 번 되돌린다 (실패는 횟수에 안 센다)
    public void decrease(AiFeature feature, Long userId, LocalDate day) {
        redis.opsForValue().decrement(key(feature, userId, day));
    }

    public long count(AiFeature feature, Long userId, LocalDate day) {
        String value = redis.opsForValue().get(key(feature, userId, day));
        return value == null ? 0 : Long.parseLong(value);
    }

    private String key(AiFeature feature, Long userId, LocalDate day) {
        return PREFIX + feature.name() + ":" + userId + ":" + day;
    }
}
