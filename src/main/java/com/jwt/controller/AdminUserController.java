package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.UserAdminDto;
import com.jwt.entity.User;
import com.jwt.service.AuthorizationService;
import com.jwt.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final AuthorizationService authorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAdminDto.Response>>> all(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getAllUsers(user).stream().map(UserAdminDto.Response::new).toList()));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<UserAdminDto.Response>>> pending(@AuthenticationPrincipal User user) {
        authorizationService.requireAdmin(user);
        return ResponseEntity.ok(ApiResponse.ok(userService.getPendingUsers().stream().map(UserAdminDto.Response::new).toList()));
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<UserAdminDto.Response>> approve(@PathVariable Long userId, @AuthenticationPrincipal User user) {
        authorizationService.requireAdmin(user);
        return ResponseEntity.ok(ApiResponse.ok("회원이 승인되었습니다.", new UserAdminDto.Response(userService.approveUser(userId))));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<UserAdminDto.Response>> reject(@PathVariable Long userId, @AuthenticationPrincipal User user) {
        authorizationService.requireAdmin(user);
        return ResponseEntity.ok(ApiResponse.ok("회원이 거절되었습니다.", new UserAdminDto.Response(userService.rejectUser(userId))));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserAdminDto.Response>> update(
            @PathVariable Long userId,
            @RequestParam String role,
            @RequestParam String status,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("회원 정보가 수정되었습니다.", new UserAdminDto.Response(userService.updateAdminFields(userId, role, status, user))));
    }
}
