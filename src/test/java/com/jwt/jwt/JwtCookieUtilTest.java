package com.jwt.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieUtilTest {

    JwtCookieUtil jwtCookieUtil;

    @BeforeEach
    void setUp() {
        jwtCookieUtil = new JwtCookieUtil();
        ReflectionTestUtils.setField(jwtCookieUtil, "accessExpirationMillis", 300_000L);
        ReflectionTestUtils.setField(jwtCookieUtil, "refreshExpirationMillis", 21_600_000L);
        ReflectionTestUtils.setField(jwtCookieUtil, "accessCookieName", "jwt_token");
        ReflectionTestUtils.setField(jwtCookieUtil, "refreshCookieName", "jwt_refresh_token");
        ReflectionTestUtils.setField(jwtCookieUtil, "secure", true);
        ReflectionTestUtils.setField(jwtCookieUtil, "sameSite", "Lax");
        ReflectionTestUtils.setField(jwtCookieUtil, "refreshCookiePath", "/");
    }

    @Test
    void productionCookieSettingsApplyToCreatedAndDeletedCookies() {
        ResponseCookie accessCookie = jwtCookieUtil.createAccessTokenCookie("access");
        ResponseCookie refreshCookie = jwtCookieUtil.createRefreshTokenCookie("refresh");
        ResponseCookie deletedRefreshCookie = jwtCookieUtil.deleteRefreshTokenCookie();

        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(accessCookie.isSecure()).isTrue();
        assertThat(accessCookie.getSameSite()).isEqualTo("Lax");
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.isSecure()).isTrue();
        assertThat(refreshCookie.getPath()).isEqualTo("/");
        assertThat(deletedRefreshCookie.getPath()).isEqualTo(refreshCookie.getPath());
        assertThat(deletedRefreshCookie.getMaxAge()).isZero();
    }
}
