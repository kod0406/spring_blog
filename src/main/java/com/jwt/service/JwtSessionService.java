package com.jwt.service;

import com.jwt.entity.User;
import com.jwt.entity.UserStatus;
import com.jwt.exception.UnauthorizedException;
import com.jwt.jwt.JwtTokenPair;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtSessionService {

    private static final String INVALID_REFRESH_TOKEN = "유효하지 않은 refresh token입니다.";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisService tokenRedisService;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    public JwtTokenPair issueTokens(User user) {
        requireActiveUser(user);
        String userId = String.valueOf(user.getUserId());
        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, user.getRole());
        tokenRedisService.saveRefreshToken(userId, refreshToken, refreshExpirationMillis);
        return new JwtTokenPair(accessToken, refreshToken);
    }

    public JwtTokenPair refresh(String currentRefreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(currentRefreshToken)) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        User user = findActiveTokenUser(currentRefreshToken);
        String userId = String.valueOf(user.getUserId());
        String replacementAccessToken = jwtTokenProvider.createAccessToken(userId, user.getRole());
        String replacementRefreshToken = jwtTokenProvider.createRefreshToken(userId, user.getRole());

        boolean rotated = tokenRedisService.rotateRefreshToken(
                userId,
                currentRefreshToken,
                replacementRefreshToken,
                refreshExpirationMillis
        );
        if (!rotated) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        return new JwtTokenPair(replacementAccessToken, replacementRefreshToken);
    }

    public void revoke(String refreshToken, User authenticatedUser) {
        if (refreshToken != null && jwtTokenProvider.validateRefreshToken(refreshToken)) {
            String userId = jwtTokenProvider.getClaim(refreshToken, "sub");
            if (userId != null) {
                tokenRedisService.deleteRefreshTokenIfMatches(userId, refreshToken);
                return;
            }
        }
        if (authenticatedUser != null) {
            tokenRedisService.deleteRefreshToken(String.valueOf(authenticatedUser.getUserId()));
        }
    }

    private User findActiveTokenUser(String refreshToken) {
        String userId = jwtTokenProvider.getClaim(refreshToken, "sub");
        if (userId == null) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }

        try {
            User user = userRepository.findById(Long.parseLong(userId))
                    .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN));
            requireActiveUser(user);
            return user;
        } catch (NumberFormatException e) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN);
        }
    }

    private void requireActiveUser(User user) {
        if (user == null || user.getStatusEnum() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("활성화된 사용자만 인증할 수 있습니다.");
        }
    }
}
