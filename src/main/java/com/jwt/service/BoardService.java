package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.entity.Board;
import com.jwt.entity.Category;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.User;
import com.jwt.exception.BadRequestException;
import com.jwt.exception.NotFoundException;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        return toResponse(boardRepository.save(board));
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getPosts(String categoryKey, Pageable pageable, User viewer) {
        return getPublicPosts(categoryKey, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getPublicPosts(String categoryKey, Pageable pageable) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return boardRepository.findPublicPosts(pageable).map(this::toResponse);
        }

        Category category = categoryRepository.findByKey(categoryKey)
                .filter(it -> Boolean.TRUE.equals(it.getActive()))
                .filter(it -> it.getVisibility() == null || it.getVisibility() == CategoryVisibility.PUBLIC)
                .orElseThrow(() -> new NotFoundException("글머리를 찾을 수 없습니다."));

        return boardRepository.findPublicPostsByCategory(category, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAdminPosts(String visibility, String categoryKey, Pageable pageable, User user) {
        authorizationService.requireAdmin(user);
        List<BoardDto.Response> filtered = boardRepository.findAllByOrderByCreatedAtDesc(Pageable.unpaged()).stream()
                .filter(board -> matchesVisibility(board, visibility))
                .filter(board -> matchesCategory(board, categoryKey))
                .map(this::toResponse)
                .toList();

        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAllBoards(Pageable pageable) {
        return getPublicPosts(null, pageable);
    }

    @Transactional(readOnly = true)
    public BoardDto.Response getBoardById(Long boardId, User viewer) {
        return toResponse(getReadablePostEntity(boardId, viewer));
    }

    @Transactional(readOnly = true)
    public BoardDto.Response getBoardById(Long boardId) {
        return getBoardById(boardId, null);
    }

    @Transactional(readOnly = true)
    public Board getReadablePostEntity(Long boardId, User viewer) {
        Board board = getPostEntity(boardId);
        if (authorizationService.isAdmin(viewer)) {
            return board;
        }
        if (isPubliclyReadable(board)) {
            return board;
        }
        throw new NotFoundException("글을 찾을 수 없습니다.");
    }

    @Transactional(readOnly = true)
    public Board getPublishedPostEntity(Long boardId) {
        return getReadablePostEntity(boardId, null);
    }

    @Transactional(readOnly = true)
    public Board getPostEntity(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException("글을 찾을 수 없습니다."));
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
        boardRepository.delete(getPostEntity(boardId));
    }

    public boolean canComment(Board board, User user) {
        if (isPrivateCategory(board)) {
            return authorizationService.isAdmin(user);
        }
        return authorizationService.isActiveUser(user);
    }

    public boolean isPrivateCategory(Board board) {
        return board.getCategory() != null && board.getCategory().getVisibility() == CategoryVisibility.PRIVATE;
    }

    public boolean isPubliclyReadable(Board board) {
        return !Boolean.FALSE.equals(board.getPublished()) && !isPrivateCategory(board);
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
                .orElseThrow(() -> new NotFoundException("글머리를 찾을 수 없습니다."));
        board.setCategory(category);
    }

    private boolean matchesVisibility(Board board, String visibility) {
        if (visibility == null || visibility.isBlank() || "all".equalsIgnoreCase(visibility)) {
            return true;
        }
        if ("public".equalsIgnoreCase(visibility)) {
            return !isPrivateCategory(board) && !Boolean.FALSE.equals(board.getPublished());
        }
        if ("private".equalsIgnoreCase(visibility)) {
            return isPrivateCategory(board);
        }
        if ("unpublished".equalsIgnoreCase(visibility)) {
            return Boolean.FALSE.equals(board.getPublished());
        }
        if ("uncategorized".equalsIgnoreCase(visibility)) {
            return board.getCategory() == null;
        }
        return true;
    }

    private boolean matchesCategory(Board board, String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return true;
        }
        return board.getCategory() != null && categoryKey.equals(board.getCategory().getKey());
    }

    private void validateRequest(BoardDto.Request requestDto) {
        if (requestDto == null || requestDto.getTitle() == null || requestDto.getTitle().trim().isEmpty()) {
            throw new BadRequestException("제목을 입력해 주세요.");
        }
        String markdown = requestDto.getContentMarkdown() != null ? requestDto.getContentMarkdown() : requestDto.getContent();
        if (markdown == null || markdown.trim().isEmpty()) {
            throw new BadRequestException("내용을 입력해 주세요.");
        }
        if (requestDto.getTitle().trim().length() > 200) {
            throw new BadRequestException("제목은 200자 이하로 입력해 주세요.");
        }
    }

    private BoardDto.Response toResponse(Board board) {
        return new BoardDto.Response(board, markdownService.render(board.getContentMarkdown()));
    }
}
