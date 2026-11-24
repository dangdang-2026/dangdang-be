package com.dangdang.domain.auth.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class EmailVerificationStore {
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    public static final Duration RESEND_WAIT = Duration.ofSeconds(60);
    public static final int MAX_TRIES = 5;
    public static final int MAX_SENDS_PER_DAY = 5;

    private static final String CODE = "email:code:";
    private static final String TRIES = "email:tries:";
    private static final String SENDS = "email:sends:";
    private static final String VERIFIED = "email:verified:";
    private static final String WAIT = "email:wait:";

    private final StringRedisTemplate redis;

    // 오늘 보낸 횟수 키. 올릴 때 받은 키를 그대로 돌려줄 때도 쓴다 (자정을 넘겨도 같은 날 키로 되돌리게)
    public String todaySendKey(String email) {
        return SENDS + email + ":" + LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    // 그 키의 보낸 횟수를 1 올리고, 올린 뒤 값을 돌려준다
    public long increaseSendCount(String sendKey) {
        Long count = redis.opsForValue().increment(sendKey);
        if (count != null && count == 1) {
            redis.expire(sendKey, Duration.ofDays(1));
        }
        return count == null ? 0 : count;
    }

    // 메일을 못 보냈으면 그 키의 횟수를 하나 돌려준다 (V-12 — 사용자 잘못이 아님). 0 밑으로는 안 내려감
    public void giveBackSend(String sendKey) {
        Long count = redis.opsForValue().decrement(sendKey);
        if (count != null && count < 0) {
            redis.delete(sendKey);   // 그 사이 키가 만료됐으면 -1로 만료 없이 남지 않게
        }
    }

    // 다시 받기 대기를 시작한다. 이미 대기 중이면 남은 초, 새로 시작했으면 0
    public long startResendWait(String email) {
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(WAIT + email, "1", RESEND_WAIT))) {
            return 0;
        }
        Long ttl = redis.getExpire(WAIT + email, TimeUnit.SECONDS);
        return ttl == null || ttl <= 0 ? 1 : ttl;   // 그 사이 막 끝났으면 1초만 기다리게
    }

    // 메일을 못 보냈으면 대기를 풀어 바로 다시 누를 수 있게
    public void clearResendWait(String email) {
        redis.delete(WAIT + email);
    }

    public void saveCode(String email, String code) {
        redis.opsForValue().set(CODE + email, code, CODE_TTL);
        redis.delete(TRIES + email); // 새 번호를 받으면 틀린 횟수 초기화
    }

    public String findCode(String email) {
        return redis.opsForValue().get(CODE + email);
    }

    // 이번 확인을 비교하기 전에 먼저 센다 (+1 뒤 값). 동시에 많이 와도 비교는 5번까지만 (리뷰: 세고 나서 비교)
    // 맞히면 markVerified가 지운다
    public long countTry(String email) {
        Long tries = redis.opsForValue().increment(TRIES + email);
        redis.expire(TRIES + email, CODE_TTL);
        return tries == null ? 0 : tries;
    }

    // 5번 틀리면 번호를 지워서 다시 받게 한다. 틀린 횟수는 남겨 둬서 V-07(5번 틀림)과 V-06(시간 지남)을 구분
    public void dropCode(String email) {
        redis.delete(CODE + email);
    }

    // 지금까지 틀린 횟수 (없으면 0). 5번 틀려 번호가 지워진 뒤에도 10분 동안 남아 있다 → V-07 구분용
    public long tries(String email) {
        String value = redis.opsForValue().get(TRIES + email);
        return value == null ? 0 : Long.parseLong(value);
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
