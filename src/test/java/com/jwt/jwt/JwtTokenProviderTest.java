package com.jwt.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", "test-secret-key-for-jwt-type-validation-1234567890");
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpirationMillis", 300_000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpirationMillis", 21_600_000L);
        jwtTokenProvider.init();
    }

    @Test
    void accessAndRefreshTokensCannotBeUsedInterchangeably() {
        String accessToken = jwtTokenProvider.createAccessToken("7", "ADMIN");
        String refreshToken = jwtTokenProvider.createRefreshToken("7", "ADMIN");

        assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse();
        assertThat(jwtTokenProvider.validateRefreshToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.validateAccessToken(refreshToken)).isFalse();
        assertThat(jwtTokenProvider.getClaim(accessToken, "type")).isEqualTo("access");
        assertThat(jwtTokenProvider.getClaim(refreshToken, "type")).isEqualTo("refresh");
        assertThat(jwtTokenProvider.getClaim(refreshToken, "jti")).isNotBlank();
    }
}
