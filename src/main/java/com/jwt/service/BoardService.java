package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.entity.Board;
import com.jwt.entity.Category;
import com.jwt.entity.User;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final CategoryRepository categoryRepository;
    private final AuthorizationService authorizationService;
    private final MarkdownService markdownService;

    @Transactional
    public BoardDto.Response createBoard(BoardDto.Request requestDto, User user) {
        authorizationService.requireAdmin(user);
        validateRequest(requestDto);

        Board board = new Board();
        applyRequest(board, requestDto);
        board.setUser(user);

        Board savedBoard = boardRepository.save(board);
        return toResponse(savedBoard);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getPublicPosts(String categoryKey, Pageable pageable) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return boardRepository.findPublicPosts(pageable).map(this::toResponse);
        }

        Category category = categoryRepository.findByKey(categoryKey)
                .filter(it -> Boolean.TRUE.equals(it.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("글머리를 찾을 수 없습니다."));

        return boardRepository.findPublicPostsByCategory(category, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAllBoards(Pageable pageable) {
        return getPublicPosts(null, pageable);
    }

    @Transactional(readOnly = true)
    public BoardDto.Response getBoardById(Long boardId) {
        Board board = getPublishedPostEntity(boardId);
        return toResponse(board);
    }

    @Transactional(readOnly = true)
    public Board getPublishedPostEntity(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("글을 찾을 수 없습니다."));
        if (Boolean.FALSE.equals(board.getPublished())) {
            throw new IllegalArgumentException("글을 찾을 수 없습니다.");
        }
        return board;
    }

    @Transactional(readOnly = true)
    public Board getPostEntity(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("글을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public BoardDto.Response getAdminPost(Long boardId, User user) {
        authorizationService.requireAdmin(user);
        return toResponse(getPostEntity(boardId));
    }

    @Transactional
    public BoardDto.Response updateBoard(Long boardId, BoardDto.Request requestDto, User user) {
        authorizationService.requireAdmin(user);
        validateRequest(requestDto);

        Board board = getPostEntity(boardId);
        applyRequest(board, requestDto);

        return toResponse(board);
    }

    @Transactional
    public void deleteBoard(Long boardId, User user) {
        authorizationService.requireAdmin(user);
        Board board = getPostEntity(boardId);
        boardRepository.delete(board);
    }

    private void applyRequest(Board board, BoardDto.Request requestDto) {
        String rawMarkdown = requestDto.getContentMarkdown() != null
                ? requestDto.getContentMarkdown()
                : requestDto.getContent();
        String markdown = rawMarkdown.trim();

        board.setTitle(requestDto.getTitle().trim());
        board.setContent(markdown);
        board.setContentMarkdown(markdown);
        board.setPublished(requestDto.getPublished() == null || requestDto.getPublished());

        if (requestDto.getCategoryKey() == null || requestDto.getCategoryKey().isBlank()) {
            board.setCategory(null);
            return;
        }

        Category category = categoryRepository.findByKey(requestDto.getCategoryKey().trim())
                .filter(it -> Boolean.TRUE.equals(it.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("글머리를 찾을 수 없습니다."));
        board.setCategory(category);
    }

    private void validateRequest(BoardDto.Request requestDto) {
        if (requestDto == null || requestDto.getTitle() == null || requestDto.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해 주세요.");
        }
        String markdown = requestDto.getContentMarkdown() != null ? requestDto.getContentMarkdown() : requestDto.getContent();
        if (markdown == null || markdown.trim().isEmpty()) {
            throw new IllegalArgumentException("내용을 입력해 주세요.");
        }
        if (requestDto.getTitle().trim().length() > 200) {
            throw new IllegalArgumentException("제목은 200자 이하로 입력해 주세요.");
        }
    }

    private BoardDto.Response toResponse(Board board) {
        return new BoardDto.Response(board, markdownService.render(board.getContentMarkdown()));
    }
}
