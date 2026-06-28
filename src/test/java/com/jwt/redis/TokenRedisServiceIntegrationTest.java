package com.jwt.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TokenRedisServiceIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    TokenRedisService tokenRedisService;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void clearRedis() {
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        try {
            connection.serverCommands().flushDb();
        } finally {
            connection.close();
        }
    }

    @Test
    void storesOnlyHashWithRedisTtl() {
        tokenRedisService.saveRefreshToken("101", "raw-refresh-token", 30_000L);

        String stored = redisTemplate.opsForValue().get("refresh_token:101");
        Long ttlMillis = redisTemplate.getExpire("refresh_token:101", TimeUnit.MILLISECONDS);

        assertThat(stored).isNotEqualTo("raw-refresh-token").hasSize(43);
        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(30_000L);
        assertThat(tokenRedisService.matchesRefreshToken("101", "raw-refresh-token")).isTrue();
    }

    @Test
    void rotationIsAtomicAndPreviousTokenCannotBeReused() {
        tokenRedisService.saveRefreshToken("102", "first-refresh", 30_000L);

        assertThat(tokenRedisService.rotateRefreshToken(
                "102", "first-refresh", "second-refresh", 30_000L)).isTrue();
        assertThat(tokenRedisService.matchesRefreshToken("102", "first-refresh")).isFalse();
        assertThat(tokenRedisService.matchesRefreshToken("102", "second-refresh")).isTrue();
        assertThat(tokenRedisService.rotateRefreshToken(
                "102", "first-refresh", "replayed-refresh", 30_000L)).isFalse();
        assertThat(tokenRedisService.deleteRefreshTokenIfMatches("102", "second-refresh")).isTrue();
        assertThat(tokenRedisService.matchesRefreshToken("102", "second-refresh")).isFalse();
    }
}
