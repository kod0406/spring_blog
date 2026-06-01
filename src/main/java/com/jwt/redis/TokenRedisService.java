package com.jwt.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final Map<String, CachedToken> fallbackTokens = new ConcurrentHashMap<>();

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    public void saveRefreshToken(String userId, String refreshToken, long expirationMillis) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(key, refreshToken, expirationMillis, TimeUnit.MILLISECONDS);
            log.info("Refresh token saved in Redis. userId={}", userId);
        } catch (RuntimeException e) {
            fallbackTokens.put(key, new CachedToken(refreshToken, Instant.now().plusMillis(expirationMillis)));
            log.warn("Redis unavailable. Refresh token saved in memory for this app instance. userId={}", userId);
        }
    }

    public void deleteRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        fallbackTokens.remove(key);
        try {
            redisTemplate.delete(key);
            log.info("Refresh token deleted. userId={}", userId);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable while deleting refresh token. userId={}", userId);
        }
    }

    public String getRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;
        try {
            String token = redisTemplate.opsForValue().get(key);
            if (token != null) {
                return token;
            }
        } catch (RuntimeException e) {
            log.warn("Redis unavailable while reading refresh token. userId={}", userId);
        }

        CachedToken cachedToken = fallbackTokens.get(key);
        if (cachedToken == null || cachedToken.expiresAt().isBefore(Instant.now())) {
            fallbackTokens.remove(key);
            return null;
        }
        return cachedToken.token();
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
