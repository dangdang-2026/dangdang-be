package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 로그인 실패 횟수와 잠금을 Redis 에 둔다 (AUTH-04: 5번 틀리면 5분 잠금)
@Component
@RequiredArgsConstructor
public class LoginAttemptStore {

    public static final int MAX_FAILS = 5;
    public static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private static final String FAILS = "login:fails:";
    private static final String LOCK = "login:lock:";

    private final StringRedisTemplate redis;

    public boolean isLocked(String email) {
        return Boolean.TRUE.equals(redis.hasKey(LOCK + email));
    }

    // 실패 +1. 5번이 되면 잠그고 횟수는 지운다. 올린 뒤 값을 돌려준다
    public long increaseFails(String email) {
        Long fails = redis.opsForValue().increment(FAILS + email);
        redis.expire(FAILS + email, LOCK_TTL); // 한동안 안 틀리면 저절로 0
        if (fails != null && fails >= MAX_FAILS) {
            redis.opsForValue().set(LOCK + email, "1", LOCK_TTL);
            redis.delete(FAILS + email);
        }
        return fails == null ? 0 : fails;
    }

    public void clear(String email) {
        redis.delete(FAILS + email);
    }

    //비밀번호를 새로 정하면 잠금도 풀어 준다.
    public void unlock(String email){
        redis.delete(FAILS + email);
        redis.delete(LOCK+email);
    }
}
