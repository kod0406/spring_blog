package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.BoardDto;
import com.jwt.entity.User;
import com.jwt.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping({"/api/posts", "/api/board"})
    public ResponseEntity<ApiResponse<Page<BoardDto.Response>>> getPosts(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(boardService.getPosts(category, pageable, user)));
    }

    @GetMapping({"/api/posts/{postId}", "/api/board/{postId}"})
    public ResponseEntity<ApiResponse<BoardDto.Response>> getPost(@PathVariable Long postId, @AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(boardService.getBoardById(postId, user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/admin/posts")
    public ResponseEntity<ApiResponse<Page<BoardDto.Response>>> getAdminPosts(
            @RequestParam(defaultValue = "all") String visibility,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(boardService.getAdminPosts(visibility, category, pageable, user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/posts")
    public ResponseEntity<ApiResponse<BoardDto.Response>> createPost(
            @RequestBody BoardDto.Request requestDto,
            @AuthenticationPrincipal User user
    ) {
        try {
            BoardDto.Response responseDto = boardService.createBoard(requestDto, user);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("글이 작성되었습니다.", responseDto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/api/admin/posts/{postId}")
    public ResponseEntity<ApiResponse<BoardDto.Response>> updatePost(
            @PathVariable Long postId,
            @RequestBody BoardDto.Request requestDto,
            @AuthenticationPrincipal User user
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("글이 수정되었습니다.", boardService.updateBoard(postId, requestDto, user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(statusFor(e)).body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user
    ) {
        try {
            boardService.deleteBoard(postId, user);
            return ResponseEntity.ok(ApiResponse.ok("글이 삭제되었습니다."));
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
