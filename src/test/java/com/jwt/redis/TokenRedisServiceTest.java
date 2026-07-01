package com.jwt.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRedisServiceTest {

    @Mock
    RedisTemplate<String, String> redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    TokenRedisService tokenRedisService;

    @BeforeEach
    void setUp() {
        tokenRedisService = new TokenRedisService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void refreshTokenIsStoredAsHashAndComparedByHash() {
        String rawToken = "header.payload.signature";
        tokenRedisService.saveRefreshToken("7", rawToken, 30_000L);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("refresh_token:7"),
                hashCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(30_000L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS)
        );
        String storedHash = hashCaptor.getValue();
        assertThat(storedHash).isNotEqualTo(rawToken).hasSize(43);

        when(valueOperations.get("refresh_token:7")).thenReturn(storedHash);
        assertThat(tokenRedisService.matchesRefreshToken("7", rawToken)).isTrue();
        assertThat(tokenRedisService.matchesRefreshToken("7", "different-token")).isFalse();
    }

    @Test
    void redisFailureIsFailClosedInsteadOfUsingLocalFallback() {
        when(valueOperations.get("refresh_token:7")).thenThrow(new RuntimeException("redis unavailable"));

        assertThatThrownBy(() -> tokenRedisService.matchesRefreshToken("7", "refresh"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증 저장소를 사용할 수 없습니다.");
    }
}
