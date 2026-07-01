package com.jwt.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

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

    @Value("${jwt.cookie-secure:false}")
    private boolean secure;

    @Value("${jwt.cookie-same-site:Lax}")
    private String sameSite;

    @Value("${jwt.refresh-cookie-path:/}")
    private String refreshCookiePath;

    public ResponseCookie createAccessTokenCookie(String jwt) {
        return ResponseCookie.from(accessCookieName, jwt)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(accessExpirationMillis / 1000)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(accessCookieName, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String jwt) {
        return ResponseCookie.from(refreshCookieName, jwt)
                .httpOnly(true)
                .secure(secure)
                .path(refreshCookiePath)
                .maxAge(refreshExpirationMillis / 1000)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(secure)
                .path(refreshCookiePath)
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }

    public void addTokenCookies(HttpServletResponse response, JwtTokenPair tokenPair) {
        response.addHeader(HttpHeaders.SET_COOKIE, createAccessTokenCookie(tokenPair.accessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(tokenPair.refreshToken()).toString());
    }

    public void deleteTokenCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, deleteAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> refreshCookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
