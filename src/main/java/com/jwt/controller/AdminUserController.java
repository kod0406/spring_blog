package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.UserAdminDto;
import com.jwt.entity.User;
import com.jwt.service.AuthorizationService;
import com.jwt.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final AuthorizationService authorizationService;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<UserAdminDto.Response>>> pending(@AuthenticationPrincipal User user) {
        try {
            authorizationService.requireAdmin(user);
            return ResponseEntity.ok(ApiResponse.ok(userService.getPendingUsers().stream().map(UserAdminDto.Response::new).toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<UserAdminDto.Response>> approve(@PathVariable Long userId, @AuthenticationPrincipal User user) {
        try {
            authorizationService.requireAdmin(user);
            return ResponseEntity.ok(ApiResponse.ok("회원이 승인되었습니다.", new UserAdminDto.Response(userService.approveUser(userId))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<UserAdminDto.Response>> reject(@PathVariable Long userId, @AuthenticationPrincipal User user) {
        try {
            authorizationService.requireAdmin(user);
            return ResponseEntity.ok(ApiResponse.ok("회원이 거절되었습니다.", new UserAdminDto.Response(userService.rejectUser(userId))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    private HttpStatus statusFor(IllegalArgumentException e) {
        String message = e.getMessage();
        if (message != null && message.contains("찾을 수 없습니다")) {
            return HttpStatus.NOT_FOUND;
        }
        if (message != null && message.contains("로그인")) {
            return HttpStatus.UNAUTHORIZED;
        }
        return HttpStatus.FORBIDDEN;
    }
}
