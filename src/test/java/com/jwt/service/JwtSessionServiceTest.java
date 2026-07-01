package com.jwt.service;

import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.exception.UnauthorizedException;
import com.jwt.jwt.JwtTokenPair;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtSessionServiceTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    TokenRedisService tokenRedisService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    JwtSessionService jwtSessionService;

    User activeUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtSessionService, "refreshExpirationMillis", 21_600_000L);
        activeUser = User.builder()
                .userId(7L)
                .email("member@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void issueStoresRefreshTokenWithConfiguredTtl() {
        when(jwtTokenProvider.createAccessToken("7", "USER")).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken("7", "USER")).thenReturn("refresh");

        JwtTokenPair result = jwtSessionService.issueTokens(activeUser);

        assertThat(result).isEqualTo(new JwtTokenPair("access", "refresh"));
        verify(tokenRedisService).saveRefreshToken("7", "refresh", 21_600_000L);
    }

    @Test
    void refreshRotatesTokenAgainstRedisValue() {
        when(jwtTokenProvider.validateRefreshToken("current-refresh")).thenReturn(true);
        when(jwtTokenProvider.getClaim("current-refresh", "sub")).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeUser));
        when(jwtTokenProvider.createAccessToken("7", "USER")).thenReturn("replacement-access");
        when(jwtTokenProvider.createRefreshToken("7", "USER")).thenReturn("replacement-refresh");
        when(tokenRedisService.rotateRefreshToken(
                "7", "current-refresh", "replacement-refresh", 21_600_000L)).thenReturn(true);

        JwtTokenPair result = jwtSessionService.refresh("current-refresh");

        assertThat(result).isEqualTo(new JwtTokenPair("replacement-access", "replacement-refresh"));
    }

    @Test
    void reusedOrUnknownRefreshTokenIsRejectedWhenRedisComparisonFails() {
        when(jwtTokenProvider.validateRefreshToken("reused-refresh")).thenReturn(true);
        when(jwtTokenProvider.getClaim("reused-refresh", "sub")).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeUser));
        when(jwtTokenProvider.createAccessToken("7", "USER")).thenReturn("unused-access");
        when(jwtTokenProvider.createRefreshToken("7", "USER")).thenReturn("unused-refresh");

        assertThatThrownBy(() -> jwtSessionService.refresh("reused-refresh"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 refresh token입니다.");
    }
}
