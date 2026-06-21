package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.dto.BoardSearchCondition;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final CategoryRepository categoryRepository;
    private final AuthorizationService authorizationService;
    private final BoardSearchSpecificationFactory boardSearchSpecificationFactory;
    private final BoardDtoMapper boardDtoMapper;

    @Transactional
    public BoardDto.Response createBoard(BoardDto.Request requestDto, User user) {
        authorizationService.requireAdmin(user);
        validateRequest(requestDto);

        Board board = new Board();
        applyRequest(board, requestDto);
        board.setUser(user);

        return boardDtoMapper.toResponse(boardRepository.save(board));
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getPosts(String categoryKey, Pageable pageable, User viewer) {
        return getPosts(categoryKey, null, null, null, pageable, viewer);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getPosts(String categoryKey,
                                            String keyword,
                                            String searchType,
                                            String sort,
                                            Pageable pageable,
                                            User viewer) {
        BoardSearchCondition condition = BoardSearchCondition.publicSearch(categoryKey, keyword, searchType, sort);
        Pageable sortedPageable = sortedPageable(pageable, condition.getSort(), 10);
        Specification<Board> specification = boardSearchSpecificationFactory.publicSpecification(condition);
        return boardRepository.findAll(specification, sortedPageable)
                .map(board -> boardDtoMapper.toListResponse(board, viewer));
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getPublicPosts(String categoryKey, Pageable pageable) {
        return getPosts(categoryKey, null, null, null, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAdminPosts(String visibility, String categoryKey, Pageable pageable, User user) {
        return getAdminPosts(visibility, categoryKey, null, null, null, pageable, user);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAdminPosts(String visibility,
                                                 String categoryKey,
                                                 String keyword,
                                                 String searchType,
                                                 String sort,
                                                 Pageable pageable,
                                                 User user) {
        authorizationService.requireAdmin(user);
        BoardSearchCondition condition = BoardSearchCondition.adminSearch(visibility, categoryKey, keyword, searchType, sort);
        Pageable sortedPageable = sortedPageable(pageable, condition.getSort(), 20);
        return boardRepository.findAll(boardSearchSpecificationFactory.adminSpecification(condition), sortedPageable)
                .map(boardDtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BoardDto.Response> getAllBoards(Pageable pageable) {
        return getPublicPosts(null, pageable);
    }

    @Transactional(readOnly = true)
    public BoardDto.Response getBoardById(Long boardId, User viewer) {
        return boardDtoMapper.toResponse(getReadablePostEntity(boardId, viewer));
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
        return boardDtoMapper.toResponse(getPostEntity(boardId));
    }

    @Transactional
    public BoardDto.Response updateBoard(Long boardId, BoardDto.Request requestDto, User user) {
        authorizationService.requireAdmin(user);
        validateRequest(requestDto);

        Board board = getPostEntity(boardId);
        applyRequest(board, requestDto);

        return boardDtoMapper.toResponse(board);
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

    private Pageable sortedPageable(Pageable pageable, String sort, int defaultSize) {
        String effectiveSort = boardSearchSpecificationFactory.effectiveSort(sort);
        int page = pageable == null ? 0 : Math.max(pageable.getPageNumber(), 0);
        int size = pageable == null ? defaultSize : pageable.getPageSize();
        size = Math.max(1, Math.min(size, 100));
        Sort.Direction direction = "oldest".equals(effectiveSort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort resolvedSort = Sort.by(direction, "createdAt").and(Sort.by(direction, "boardId"));
        return PageRequest.of(page, size, resolvedSort);
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

}
