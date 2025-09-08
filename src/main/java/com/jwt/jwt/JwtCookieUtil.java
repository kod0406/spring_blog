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

    /**
     * Access Token을 담을 쿠키를 생성합니다.
     * @param jwt Access Token 값
     * @return 생성된 ResponseCookie 객체
     */
    public ResponseCookie createAccessTokenCookie(String jwt) {
        return ResponseCookie.from(accessCookieName, jwt)
                // JavaScript에서 쿠키에 접근할 수 있도록 false로 설정합니다.
                // (React 등의 클라이언트 라이브러리에서 토큰을 읽어야 할 때 필요)
                .httpOnly(/*TODO 7: JavaScript 접근을 허용하려면 어떤 값을 넣어야 할까요? */)
                .secure(false) // localhost 환경에서는 https가 아니므로 false로 설정
                .path("/") // 쿠키가 사용될 경로를 전체 경로로 설정
                .maxAge(accessExpirationMillis / 1000) // 쿠키의 만료 시간 설정 (초 단위)
                .sameSite("Lax") // CSRF 공격 방지를 위한 설정
                .build();
    }

    /**
     * Access Token 쿠키를 삭제합니다.
     * @return 삭제 처리가 된 ResponseCookie 객체
     */
    public ResponseCookie deleteAccessTokenCookie() {
        return ResponseCookie.from(accessCookieName, "")
                .httpOnly(false)
                .secure(false)
                .path("/")
                //TODO 8: 쿠키를 즉시 만료시켜 삭제하려면 maxAge를 어떤 값으로 설정해야 할까요?
                .maxAge(/* ________ */)
                .sameSite("Lax")
                .build();
    }

    /**
     * Refresh Token을 담을 쿠키를 생성합니다.
     * @param jwt Refresh Token 값
     * @return 생성된 ResponseCookie 객체
     */
    public ResponseCookie createRefreshTokenCookie(String jwt) {
        return ResponseCookie.from(refreshCookieName, jwt)
                //TODO 9: 보안을 위해 JavaScript에서 쿠키에 접근할 수 없도록 설정해 보세요.
                .httpOnly(/* ________ */)
                .secure(false)
                .path("/")
                .maxAge(refreshExpirationMillis / 1000)
                .sameSite("Lax")
                .build();
    }

    /**
     * Refresh Token 쿠키를 삭제합니다.
     */
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0) // 만료 시간을 0으로 만들어 즉시 삭제
                .sameSite("Lax")
                .build();
    }
}