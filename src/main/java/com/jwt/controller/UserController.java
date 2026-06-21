package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.RegistrationDto;
import com.jwt.dto.loginDto;
import com.jwt.entity.User;
import com.jwt.exception.BadRequestException;
import com.jwt.jwt.JwtCookieUtil;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.redis.TokenRedisService;
import com.jwt.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisService tokenRedisService;
    private final JwtCookieUtil jwtCookieUtil;

    @Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody RegistrationDto requestDto) {
        userService.registerUser(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody loginDto logindto, HttpServletResponse response) {
        User user = userService.authenticateUser(logindto.getEmail(), logindto.getPassword());

        String accessToken = jwtTokenProvider.createAccessToken(String.valueOf(user.getUserId()), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(String.valueOf(user.getUserId()), user.getRole());

        ResponseCookie accessTokenCookie = jwtCookieUtil.createAccessTokenCookie(accessToken);
        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        ResponseCookie refreshTokenCookie = jwtCookieUtil.createRefreshTokenCookie(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        tokenRedisService.saveRefreshToken(String.valueOf(user.getUserId()), refreshToken, refreshExpirationMillis);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());

        return ResponseEntity.ok(ApiResponse.ok("로그인되었습니다.", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal User user, HttpServletResponse response) {
        if (user != null) {
            tokenRedisService.deleteRefreshToken(String.valueOf(user.getUserId()));
        }

        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieUtil.deleteAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieUtil.deleteRefreshTokenCookie().toString());
        return ResponseEntity.ok(ApiResponse.ok("로그아웃되었습니다."));
    }

    @PostMapping("/reset-password/check-email")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkEmailForReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Map<String, Boolean> data = new HashMap<>();

        try {
            userService.findByEmail(email);
            data.put("exists", true);
            return ResponseEntity.ok(ApiResponse.ok(data));
        } catch (Exception e) {
            data.put("exists", false);
            return ResponseEntity.ok(ApiResponse.ok(data));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("newPassword");
        String confirmPassword = request.get("confirmPassword");

        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BadRequestException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }

        userService.updatePassword(email, newPassword);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 변경되었습니다."));
    }
}
