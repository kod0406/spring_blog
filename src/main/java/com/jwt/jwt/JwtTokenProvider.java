package com.jwt.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
        // secretKey를 이용해 Key 객체 생성
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String createAccessToken(String userId, String role){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMillis);
        Map<String,Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("role", role);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(String userId, String role){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMillis);
        Map<String,Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("role", role);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getClaim(String token, String claimKey) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get(claimKey, String.class);
        } catch (JwtException e) {
            log.error("JWT 토큰에서 클레임을 추출하는 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT 토큰 검증 실패: {}", e.getMessage());
            return false;
        }
    }

}
