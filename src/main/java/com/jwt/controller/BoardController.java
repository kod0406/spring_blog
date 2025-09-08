package com.jwt.controller;

import com.jwt.dto.BoardDto;
import com.jwt.entity.User;
import com.jwt.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 게시글 생성
     * @param requestDto 게시글 제목, 내용
     * @param user 현재 로그인한 사용자 정보 (JWT 토큰으로 인증)
     * @return 생성된 게시글 정보
     */
    @PostMapping("/")
    public ResponseEntity<?> createBoard(@RequestBody BoardDto.Request requestDto, @AuthenticationPrincipal User user) {
        try {
            BoardDto.Response responseDto = boardService.createBoard(requestDto, user);
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("게시글 생성 실패: " + e.getMessage());
        }
    }

    /**
     * 모든 게시글 조회 (최신순, 10개씩 페이징)
     * @param pageable 페이징 정보 (예: /api/board?page=0&size=10)
     * @return 페이징된 게시글 목록
     */
    @GetMapping("/")
    public ResponseEntity<Page<BoardDto.Response>> getAllBoards(@PageableDefault(size = 10) Pageable pageable) {
        Page<BoardDto.Response> boards = boardService.getAllBoards(pageable);
        return ResponseEntity.ok(boards);
    }

    /**
     * 특정 게시글 조회
     * @param boardId 조회할 게시글 ID
     * @return 조회된 게시글 정보
     */
    @GetMapping("/{boardId}")
    public ResponseEntity<?> getBoardById(@PathVariable Long boardId) {
        try {
            BoardDto.Response responseDto = boardService.getBoardById(boardId);
            return ResponseEntity.ok(responseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 게시글 수정
     * @param boardId 수정할 게시글 ID
     * @param requestDto 수정할 제목, 내용
     * @param user 현재 로그인한 사용자 정보
     * @return 수정된 게시글 정보
     */
    @PutMapping("/{boardId}")
    public ResponseEntity<?> updateBoard(@PathVariable Long boardId, @RequestBody BoardDto.Request requestDto, @AuthenticationPrincipal User user) {
        try {
            BoardDto.Response responseDto = boardService.updateBoard(boardId, requestDto, user);
            return ResponseEntity.ok(responseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 게시글 삭제
     * @param boardId 삭제할 게시글 ID
     * @param user 현재 로그인한 사용자 정보
     * @return 성공 메시지
     */
    @DeleteMapping("/{boardId}")
    public ResponseEntity<String> deleteBoard(@PathVariable Long boardId, @AuthenticationPrincipal User user) {
        try {
            boardService.deleteBoard(boardId, user);
            return ResponseEntity.ok("게시글이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

