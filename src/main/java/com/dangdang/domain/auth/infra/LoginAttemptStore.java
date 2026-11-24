package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

// 로그인 실패 횟수와 잠금을 Redis 에 둔다 (AUTH-04: 5번 틀리면 5분 잠금)
@Component
@RequiredArgsConstructor
public class LoginAttemptStore {

    public static final int MAX_FAILS = 5;
    public static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private static final String FAILS = "login:fails:";
    private static final String LOCK = "login:lock:";

    private final StringRedisTemplate redis;

    // 잠겨 있으면 풀릴 때까지 남은 초, 아니면 0
    public long lockSecondsLeft(String email) {
        Long ttl = redis.getExpire(LOCK + email, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;   // -2 = 키 없음, -1 = 만료 없음(생기면 안 됨)
    }

    // 이번 시도를 비교하기 전에 먼저 센다 (+1 뒤 값). Redis INCR은 동시에 와도 1, 2, 3…이 겹치지 않아서
    // 동시 요청 수백 개가 와도 비밀번호를 비교하는 건 5번까지만 된다. 성공하면 clear로 0
    public long countAttempt(String email) {
        Long attempts = redis.opsForValue().increment(FAILS + email);
        redis.expire(FAILS + email, LOCK_TTL); // 한동안 안 틀리면 저절로 0
        return attempts == null ? 0 : attempts;
    }

    // 5분 잠금. 센 횟수는 지운다 (풀리면 다시 5번)
    public void lock(String email) {
        redis.opsForValue().set(LOCK + email, "1", LOCK_TTL);
        redis.delete(FAILS + email);
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
