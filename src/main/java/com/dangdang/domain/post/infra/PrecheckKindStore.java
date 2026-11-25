package com.dangdang.domain.post.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

// 올리기 전 확인(precheck)에서 걸린 종류를 30분 기억한다. Redis post:precheck:{회원 번호}:{제목·본문 해시} = "DOSE,MIND"
// 글쓰기 때 postedDespite가 정말 이 글로 안내를 본 것인지 맞춰 본다 (아무 글에나 붙여 운영자 처리함을 채우지 못하게)
// 글 내용은 Redis에 남기지 않고 해시만 쓴다. Redis가 안 되면 기억 못 한 것으로 보고 글쓰기를 막지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PrecheckKindStore {

    static final Duration TTL = Duration.ofMinutes(30);
    private static final String PREFIX = "post:precheck:";

    private final StringRedisTemplate redis;

    public void save(Long userId, String title, String content, Collection<String> kinds) {
        if (kinds.isEmpty()) {
            return;
        }
        try {
            redis.opsForValue().set(key(userId, title, content), String.join(",", kinds), TTL);
        } catch (DataAccessException e) {
            log.warn("[Precheck] 걸린 종류 저장 실패: {}", e.getClass().getSimpleName());
        }
    }

    // 같은 사람이 같은 내용(앞뒤·겹친 공백 무시)으로 확인했을 때 걸린 종류. 없으면 빈 집합
    public Set<String> find(Long userId, String title, String content) {
        try {
            String value = redis.opsForValue().get(key(userId, title, content));
            return value == null ? Set.of() : Arrays.stream(value.split(",")).collect(Collectors.toUnmodifiableSet());
        } catch (DataAccessException e) {
            log.warn("[Precheck] 걸린 종류 읽기 실패: {}", e.getClass().getSimpleName());
            return Set.of();
        }
    }

    private static String key(Long userId, String title, String content) {
        String text = normalize(title) + "\n" + normalize(content);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return PREFIX + userId + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);       // SHA-256은 모든 JVM에 있다
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }
}
