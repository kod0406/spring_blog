package com.jwt.controller;

import com.jwt.dto.ApiResponse;
import com.jwt.dto.BoardDto;
import com.jwt.entity.User;
import com.jwt.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(boardService.getPosts(category, keyword, searchType, sort, pageable(page, size), user)));
    }

    @GetMapping({"/api/posts/{postId}", "/api/board/{postId}"})
    public ResponseEntity<ApiResponse<BoardDto.Response>> getPost(@PathVariable Long postId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(boardService.getBoardById(postId, user)));
    }

    @GetMapping("/api/admin/posts")
    public ResponseEntity<ApiResponse<Page<BoardDto.Response>>> getAdminPosts(
            @RequestParam(defaultValue = "all") String visibility,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(boardService.getAdminPosts(visibility, category, keyword, searchType, sort, pageable(page, size), user)));
    }

    @PostMapping("/api/admin/posts")
    public ResponseEntity<ApiResponse<BoardDto.Response>> createPost(
            @RequestBody BoardDto.Request requestDto,
            @AuthenticationPrincipal User user
    ) {
        BoardDto.Response responseDto = boardService.createBoard(requestDto, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("글이 작성되었습니다.", responseDto));
    }

    @PutMapping("/api/admin/posts/{postId}")
    public ResponseEntity<ApiResponse<BoardDto.Response>> updatePost(
            @PathVariable Long postId,
            @RequestBody BoardDto.Request requestDto,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("글이 수정되었습니다.", boardService.updateBoard(postId, requestDto, user)));
    }

    @DeleteMapping("/api/admin/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal User user
    ) {
        boardService.deleteBoard(postId, user);
        return ResponseEntity.ok(ApiResponse.ok("글이 삭제되었습니다."));
    }

    private Pageable pageable(int page, int size) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
