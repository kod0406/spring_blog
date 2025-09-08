package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.entity.Board;
import com.jwt.entity.User;
import com.jwt.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    /**
     * 게시글 생성
     * @param requestDto 게시글 생성 요청 데이터
     * @param user 현재 인증된 사용자 정보
     * @return 생성된 게시글 정보
     */
    @Transactional
    public BoardDto.Response createBoard(BoardDto.Request requestDto, User user) {
        Board board = new Board();
        board.setTitle(requestDto.getTitle());
        board.setContent(requestDto.getContent());
        board.setUser(user);
        board.setCreatedAt(LocalDateTime.now());
        board.setUpdatedAt(LocalDateTime.now());

        Board savedBoard = boardRepository.save(board);
        return new BoardDto.Response(savedBoard);
    }

    /**
     * 모든 게시글 조회 (페이징 처리)
     * @param pageable 페이징 정보
     * @return 페이징된 게시글 목록
     */
    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAllBoards(Pageable pageable) {
        return boardRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(BoardDto.Response::new);
    }

    /**
     * 특정 게시글 조회
     * @param boardId 조회할 게시글 ID
     * @return 조회된 게시글 정보
     */
    @Transactional(readOnly = true)
    public BoardDto.Response getBoardById(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 게시글이 없습니다: " + boardId));
        return new BoardDto.Response(board);
    }

    /**
     * 게시글 수정
     * @param boardId 수정할 게시글 ID
     * @param requestDto 수정할 내용
     * @param user 현재 인증된 사용자
     * @return 수정된 게시글 정보
     */
    @Transactional
    public BoardDto.Response updateBoard(Long boardId, BoardDto.Request requestDto, User user) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 게시글이 없습니다: " + boardId));

        // 게시글 작성자만 수정 가능
        if (board.getUser().getUserId() != user.getUserId()) {
            throw new IllegalArgumentException("게시글을 수정할 권한이 없습니다.");
        }

        board.setTitle(requestDto.getTitle());
        board.setContent(requestDto.getContent());
        board.setUpdatedAt(LocalDateTime.now());

        Board updatedBoard = boardRepository.save(board);
        return new BoardDto.Response(updatedBoard);
    }

    /**
     * 게시글 삭제
     * @param boardId 삭제할 게시글 ID
     * @param user 현재 인증된 사용자
     */
    @Transactional
    public void deleteBoard(Long boardId, User user) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 게시글이 없습니다: " + boardId));

        // 게시글 작성자만 삭제 가능
        if (board.getUser().getUserId() != user.getUserId()) {
            throw new IllegalArgumentException("게시글을 삭제할 권한이 없습니다.");
        }

        boardRepository.delete(board);
    }
}
