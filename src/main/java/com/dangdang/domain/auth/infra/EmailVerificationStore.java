package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class EmailVerificationStore {
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    public static final int MAX_TRIES = 5;
    public static final int MAX_SENDS_PER_DAY = 5;

    private static final String CODE = "email:code:";
    private static final String TRIES = "email:tries:";
    private static final String SENDS = "email:sends:";
    private static final String VERIFIED = "email:verified:";

    private final StringRedisTemplate redis;

    // 오늘 보낸 횟수를 1 올리고, 올린 뒤 값을 돌려준다
    public long increaseSendCount(String email) {
        String key = SENDS + email + ":" + LocalDate.now(ZoneId.of("Asia/Seoul"));
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofDays(1));
        }
        return count == null ? 0 : count;
    }

    public void saveCode(String email, String code) {
        redis.opsForValue().set(CODE + email, code, CODE_TTL);
        redis.delete(TRIES + email); // 새 번호를 받으면 틀린 횟수 초기화
    }

    public String findCode(String email) {
        return redis.opsForValue().get(CODE + email);
    }

    // 틀린 횟수 +1. 5번이 되면 번호를 지워서 다시 받게 한다
    public long increaseTries(String email) {
        Long tries = redis.opsForValue().increment(TRIES + email);
        redis.expire(TRIES + email, CODE_TTL);
        if (tries != null && tries >= MAX_TRIES) {
            redis.delete(CODE + email);
        }
        return tries == null ? 0 : tries;
    }

    public void markVerified(String email) {
        redis.delete(CODE + email);
        redis.delete(TRIES + email);
        redis.opsForValue().set(VERIFIED + email, "1", VERIFIED_TTL);
    }

    public boolean isVerified(String email) {
        return Boolean.TRUE.equals(redis.hasKey(VERIFIED + email));
    }

    public void clearVerified(String email) {
        redis.delete(VERIFIED + email);
    }
}
