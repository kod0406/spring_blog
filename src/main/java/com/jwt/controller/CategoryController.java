package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.CategoryDto;
import com.jwt.entity.User;
import com.jwt.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping("/api/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto.Response>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getActiveCategories()));
    }

    @GetMapping("/api/admin/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto.Response>>> getAdminCategories(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(categoryService.getAllCategories(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/categories")
    public ResponseEntity<ApiResponse<CategoryDto.Response>> create(
            @RequestBody CategoryDto.Request request,
            @AuthenticationPrincipal User user
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("글머리가 생성되었습니다.", categoryService.create(request, user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/api/admin/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryDto.Response>> update(
            @PathVariable Long categoryId,
            @RequestBody CategoryDto.Request request,
            @AuthenticationPrincipal User user
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("글머리가 수정되었습니다.", categoryService.update(categoryId, request, user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/categories/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User user
    ) {
        try {
            categoryService.delete(categoryId, user);
            return ResponseEntity.ok(ApiResponse.ok("글머리가 삭제되었습니다."));
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
        if (message != null && (message.contains("권한") || message.contains("승인"))) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
