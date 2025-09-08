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

    // application.properties에서 정의한 시크릿 키를 가져옵니다.
    @Value("${jwt.secret}")
    private String secretKey;

    // Access Token 만료 시간을 가져옵니다.
    @Value("${jwt.expiration-Millis}")
    private long accessExpirationMillis;

    // Refresh Token 만료 시간을 가져옵니다.
    @Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    // JWT 서명에 사용할 키 객체입니다.
    private Key key;

    // 의존성 주입이 완료된 후, Key 객체를 초기화합니다.
    @PostConstruct
    protected void init() {
        // secretKey를 바이트 배열로 변환하여 HMAC-SHA 알고리즘에 맞는 Key 객체를 생성합니다.
        this.key = /* TODO 1: secretKey를 사용하여 Key 객체를 생성해 보세요. (힌트: Keys.hmacShaKeyFor()) */
    }

    /**
     * Access Token을 생성하는 메서드
     * @param userId 사용자 ID
     * @param role 사용자 권한
     * @return 생성된 Access Token 문자열
     */
    public String createAccessToken(String userId, String role){
        Date now = new Date();
        // 만료 시간 설정: 현재 시간 + 설정된 만료 시간
        Date expiry = new Date(now.getTime() + /* TODO 2: Access Token의 만료 시간을 설정해 보세요. */);

        Map<String,Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims) // 토큰에 담을 정보(payload) 설정
                .setIssuedAt(now) // 토큰 발급 시간 설정
                .setExpiration(expiry) // 토큰 만료 시간 설정
                .signWith(key, SignatureAlgorithm.HS256) // 사용할 암호화 알고리즘과 키 설정
                ./* TODO 3: 설정이 완료된 토큰을 문자열 형태로 만들어 반환해 보세요. (힌트: .compact()) */
    }

    /**
     * Refresh Token을 생성하는 메서드 (Access Token과 로직은 동일)
     */
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

    /**
     * 토큰에서 특정 클레임(정보)을 추출하는 메서드
     * @param token 정보룰 추출할 토큰
     * @param claimKey 추출할 정보의 키 값
     * @return 추출된 정보(값)
     */
    public String getClaim(String token, String claimKey) {
        try {
            // 토큰을 파싱(해석)하여 본문(body)을 가져옵니다.
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key) // 검증에 사용할 키 설정
                    .build()
                    .parseClaimsJws(token)
                    ./* TODO 4: 토큰의 payload 부분인 Claims를 가져와 보세요. (힌트: .getBody()) */;
            return claims.get(claimKey, String.class);
        } catch (JwtException e) {
            log.error("JWT 토큰에서 클레임을 추출하는 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰의 유효성을 검증하는 메서드
     * @param token 검증할 토큰
     * @return 유효하면 true, 아니면 false
     */
    public boolean validateToken(String token) {
        try {
            // 토큰을 파싱하여 유효한지 확인합니다. 만료되었거나, 서명이 다르면 예외가 발생합니다.
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return /* TODO 5: 유효성 검증에 성공했을 때 어떤 값을 반환해야 할까요? */;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT 토큰 검증 실패: {}", e.getMessage());
            return /* TODO 6: 유효성 검증에 실패했을 때 어떤 값을 반환해야 할까요? */;
        }
    }
}