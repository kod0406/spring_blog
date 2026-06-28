package com.jwt.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-Millis}")
    private long accessExpirationMillis;

    @Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    private Key key;

    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String userId, String role){
        return createToken(userId, role, JwtTokenType.ACCESS, accessExpirationMillis);
    }

    public String createRefreshToken(String userId, String role){
        return createToken(userId, role, JwtTokenType.REFRESH, refreshExpirationMillis);
    }

    private String createToken(String userId, String role, JwtTokenType tokenType, long expirationMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .claim("type", tokenType.getValue())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getClaim(String token, String claimKey) {
        try {
            Claims claims = parseClaims(token);
            return claims.get(claimKey, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT 토큰에서 클레임을 추출하는 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateAccessToken(String token) {
        return validateTokenType(token, JwtTokenType.ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, JwtTokenType.REFRESH);
    }

    private boolean validateTokenType(String token, JwtTokenType expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.getValue().equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
