package com.jwt.controller;

import com.jwt.dto.RegistrationDto;
import com.jwt.entity.User;
import com.jwt.service.UserService;
import com.jwt.dto.loginDto;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.redis.TokenRedisService;
import com.jwt.jwt.JwtCookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/user/")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRedisService tokenRedisService;
    private final JwtCookieUtil jwtCookieUtil;
    @org.springframework.beans.factory.annotation.Value("${jwt.refresh-Millis}")
    private long refreshExpirationMillis;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationDto requestDto) {
        try{
            userService.registerUser(requestDto);
        } catch(IllegalArgumentException e){
            log.error("[회원가입 실패] {}", e.getMessage());
            return ResponseEntity.badRequest().body("회원가입 실패: " + e.getMessage());
        }
        return ResponseEntity.ok("회원가입 성공");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody loginDto logindto, HttpServletResponse response) {
        try{
            User user = userService.authenticateUser(logindto.getEmail(), logindto.getPassword());

            log.info("[로그인 인증 성공] 사용자 이름: {}, 이메일: {}", user.getName(), user.getEmail());

            // JWT 액세스 토큰 생성
            String accessToken = jwtTokenProvider.createAccessToken(String.valueOf(user.getUserId()), user.getRole());
            // JWT 리프레시 토큰 생성
            String refreshToken = jwtTokenProvider.createRefreshToken(String.valueOf(user.getUserId()), user.getRole());

            // 토큰을 쿠키에 저장
            ResponseCookie accessTokenCookie = jwtCookieUtil.createAccessTokenCookie(accessToken);
            response.addHeader("Set-Cookie", accessTokenCookie.toString());
            ResponseCookie refreshTokenCookie = jwtCookieUtil.createAccessTokenCookie(accessToken);
            response.addHeader("Set-Cookie", refreshTokenCookie.toString());

            // 리프레시 토큰을 Redis에 저장
            tokenRedisService.saveRefreshToken(String.valueOf(user.getUserId()), refreshToken, refreshExpirationMillis);

            return ResponseEntity.ok("로그인 성공");
        } catch(Exception e) {
            log.error("[로그인 실패] {}", e.getMessage());
            return ResponseEntity.badRequest().body("로그인 실패: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal User user, HttpServletResponse response) {
        try{

            // Redis에서 리프레시 토큰 삭제
            tokenRedisService.deleteRefreshToken(String.valueOf(user.getUserId()));

            // 쿠키 삭제
            ResponseCookie deleteAccessTokenCookie = jwtCookieUtil.deleteAccessTokenCookie();
            response.addHeader("Set-Cookie", deleteAccessTokenCookie.toString());
            ResponseCookie deleteRefreshTokenCookie = jwtCookieUtil.deleteRefreshTokenCookie();
            response.addHeader(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie.toString());
            log.info("[로그아웃 완료] 사용자: {}", user.getName());
            return ResponseEntity.ok("로그아웃 성공");
        } catch(Exception e) {
            log.error("[로그아웃 실패] {}", e.getMessage());
            return ResponseEntity.badRequest().body("로그아웃 실패: " + e.getMessage());
        }
    }

    // 이메일 확인 API (비밀번호 재설정용)
    @PostMapping("/reset-password/check-email")
    public ResponseEntity<?> checkEmailForReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Map<String, Boolean> response = new HashMap<>();

        try {
            // UserService를 통해 이메일로 사용자 조회
            User user = userService.findByEmail(email);
            response.put("success", true);
            log.info("[API 비밀번호 재설정] 이메일 확인 성공: {}", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            log.warn("[API 비밀번호 재설정] 이메일 확인 실패: {}", email);
            return ResponseEntity.ok(response);
        }
    }

    // 비밀번호 재설정 API
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String newPassword = request.get("newPassword");
            String confirmPassword = request.get("confirmPassword");

            // 비밀번호 확인
            if (!newPassword.equals(confirmPassword)) {
                return ResponseEntity.badRequest().body("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
            }


            // 사용자 조회 및 비밀번호 변경
            userService.updatePassword(email, newPassword);

            log.info("[API 비밀번호 재설정 완료] 사용자 이메일: {}", email);
            return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");

        } catch (IllegalArgumentException e) {
            log.error("[API 비밀번호 재설정 실패] {}", e.getMessage());
            return ResponseEntity.badRequest().body("비밀번호 재설정 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("[API 비밀번호 재설정 오류] {}", e.getMessage());
            return ResponseEntity.badRequest().body("시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
