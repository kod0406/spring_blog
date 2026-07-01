package com.jwt.redis;

import com.jwt.emum.EmailVerificationResult;
import com.jwt.service.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TokenRedisServiceIntegrationTest {

    private static final String REDIS_PASSWORD = "test-redis-password-0123456789abcdef";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
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

    @Test
    void emailVerificationCodeUsesRedisTtlAndIsConsumedAfterSuccess() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        EmailService emailService = new EmailService(mailSender, redisTemplate);

        emailService.sendVerificationEmail("member@example.com");

        String key = "email_verification:registration:member@example.com";
        String storedCode = redisTemplate.opsForValue().get(key);
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(storedCode).matches("[0-9]{6}");
        assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(300L);

        assertThat(emailService.verifyEmailCodeWithDetails("member@example.com", storedCode))
                .isEqualTo(EmailVerificationResult.SUCCESS);
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }
}
