package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.EmailDto;
import com.jwt.dto.PasswordResetDto;
import com.jwt.dto.RegistrationDto;
import com.jwt.dto.loginDto;
import com.jwt.entity.User;
import com.jwt.jwt.JwtCookieUtil;
import com.jwt.jwt.JwtTokenPair;
import com.jwt.service.JwtSessionService;
import com.jwt.service.UserAccountRecoveryService;
import com.jwt.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
    private final UserAccountRecoveryService userAccountRecoveryService;
    private final JwtSessionService jwtSessionService;
    private final JwtCookieUtil jwtCookieUtil;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@RequestBody RegistrationDto requestDto) {
        userService.registerUser(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다. 관리자 승인 후 로그인할 수 있습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody loginDto logindto, HttpServletResponse response) {
        User user = userService.authenticateUser(logindto.getEmail(), logindto.getPassword());

        JwtTokenPair tokenPair = jwtSessionService.issueTokens(user);
        jwtCookieUtil.addTokenCookies(response, tokenPair);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());

        return ResponseEntity.ok(ApiResponse.ok("로그인되었습니다.", data));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<Void>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtCookieUtil.extractRefreshToken(request);
        JwtTokenPair tokenPair = jwtSessionService.refresh(refreshToken);
        jwtCookieUtil.addTokenCookies(response, tokenPair);
        return ResponseEntity.ok(ApiResponse.ok("토큰이 갱신되었습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal User user,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        try {
            jwtSessionService.revoke(jwtCookieUtil.extractRefreshToken(request), user);
        } finally {
            jwtCookieUtil.deleteTokenCookies(response);
        }
        return ResponseEntity.ok(ApiResponse.ok("로그아웃되었습니다."));
    }

    @PostMapping({"/reset-password/request-code", "/reset-password/check-email"})
    public ResponseEntity<ApiResponse<Void>> requestPasswordResetCode(@RequestBody EmailDto request) {
        userAccountRecoveryService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("계정이 존재하면 인증 코드가 이메일로 발송됩니다."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody PasswordResetDto request) {
        userAccountRecoveryService.resetPassword(
                request.getEmail(),
                request.getVerificationCode(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 변경되었습니다."));
    }
}
