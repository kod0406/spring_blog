package com.jwt.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRedisService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
                return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    public void saveRefreshToken(String userId, String refreshToken, long expirationMillis) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        String tokenHash = hash(refreshToken);
        try {
            redisTemplate.opsForValue().set(key, tokenHash, expirationMillis, TimeUnit.MILLISECONDS);
            log.info("Refresh token saved in Redis. userId={}", userId);
        } catch (RuntimeException e) {
            throw tokenStoreUnavailable(e);
        }
    }

    public boolean matchesRefreshToken(String userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        String expectedHash = hash(refreshToken);
        try {
            String storedHash = redisTemplate.opsForValue().get(key);
            return storedHash != null && secureEquals(storedHash, expectedHash);
        } catch (RuntimeException e) {
            throw tokenStoreUnavailable(e);
        }
    }

    public boolean rotateRefreshToken(String userId, String currentToken, String replacementToken, long expirationMillis) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        String currentHash = hash(currentToken);
        String replacementHash = hash(replacementToken);
        try {
            Long result = redisTemplate.execute(
                    ROTATE_SCRIPT,
                    List.of(key),
                    currentHash,
                    replacementHash,
                    String.valueOf(expirationMillis)
            );
            if (Long.valueOf(1L).equals(result)) {
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            throw tokenStoreUnavailable(e);
        }
    }

    public boolean deleteRefreshTokenIfMatches(String userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        String expectedHash = hash(refreshToken);
        try {
            Long result = redisTemplate.execute(DELETE_IF_MATCHES_SCRIPT, List.of(key), expectedHash);
            if (Long.valueOf(1L).equals(result)) {
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            throw tokenStoreUnavailable(e);
        }
    }

    public void deleteRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        try {
            redisTemplate.delete(key);
            log.info("Refresh token deleted. userId={}", userId);
        } catch (RuntimeException e) {
            throw tokenStoreUnavailable(e);
        }
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be blank.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private IllegalStateException tokenStoreUnavailable(RuntimeException cause) {
        log.error("Redis refresh token store is unavailable.", cause);
        return new IllegalStateException("인증 저장소를 사용할 수 없습니다.", cause);
    }
}
