package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.CommentDto;
import com.jwt.entity.User;
import com.jwt.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommentDto.Response>>> getComments(@PathVariable Long postId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getTree(postId, user)));
    }

    @GetMapping("/api/admin/comments")
    public ResponseEntity<ApiResponse<List<CommentDto.Response>>> getAll(
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getAllComments(user, postId, author, deleted, keyword)));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentDto.Response>> create(
            @PathVariable Long postId,
            @RequestBody CommentDto.Request request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("댓글이 작성되었습니다.", commentService.create(postId, request, user)));
    }

    @PostMapping("/api/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<CommentDto.Response>> reply(
            @PathVariable Long commentId,
            @RequestBody CommentDto.Request request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("답글이 작성되었습니다.", commentService.reply(commentId, request, user)));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User user
    ) {
        commentService.delete(commentId, user);
        return ResponseEntity.ok(ApiResponse.ok("댓글이 삭제되었습니다."));
    }

    @DeleteMapping("/api/admin/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> adminDelete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal User user
    ) {
        commentService.adminDelete(commentId, user);
        return ResponseEntity.ok(ApiResponse.ok("댓글이 삭제되었습니다."));
    }
}
