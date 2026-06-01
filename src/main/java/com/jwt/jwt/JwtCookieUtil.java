package com.jwt.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class JwtCookieUtil {

    @Value("${jwt.expiration-Millis}")
    private long accessExpirationMillis;

    @Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    @Value("${jwt.access-cookie-name}")
    private String accessCookieName;

    @Value("${jwt.refresh-cookie-name}")
    private String refreshCookieName;

    public ResponseCookie createAccessTokenCookie(String jwt) {
        return ResponseCookie.from(accessCookieName, jwt)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(accessExpirationMillis / 1000)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(accessCookieName, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String jwt) {
        return ResponseCookie.from(refreshCookieName, jwt)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(refreshExpirationMillis / 1000)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
