package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.dto.BoardSearchCondition;
import com.jwt.entity.Board;
import com.jwt.entity.Category;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.Comment;
import com.jwt.entity.User;
import com.jwt.exception.BadRequestException;
import com.jwt.exception.NotFoundException;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.CategoryRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        validatePublicCondition(condition);
        Pageable sortedPageable = sortedPageable(pageable, condition.getSort(), 10);
        Specification<Board> specification = publicSpecification(condition);
        return boardRepository.findAll(specification, sortedPageable)
                .map(board -> toListResponse(board, viewer));
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
        validateAdminCondition(condition);
        Pageable sortedPageable = sortedPageable(pageable, condition.getSort(), 20);
        return boardRepository.findAll(adminSpecification(condition), sortedPageable)
                .map(this::toResponse);
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

    private Specification<Board> publicSpecification(BoardSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(publishedPredicate(root, cb));
            addCategoryPredicate(predicates, root, cb, condition.getCategory(), true);
            addKeywordPredicate(predicates, root, query, cb, condition.getKeyword(), effectiveSearchType(condition.getSearchType()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Board> adminSpecification(BoardSearchCondition condition) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addVisibilityPredicate(predicates, root, cb, effectiveVisibility(condition.getVisibility()));
            addCategoryPredicate(predicates, root, cb, condition.getCategory(), false);
            addKeywordPredicate(predicates, root, query, cb, condition.getKeyword(), effectiveSearchType(condition.getSearchType()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate publishedPredicate(Root<Board> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.or(cb.isNull(root.get("published")), cb.isTrue(root.get("published")));
    }

    private void addVisibilityPredicate(List<Predicate> predicates,
                                        Root<Board> root,
                                        jakarta.persistence.criteria.CriteriaBuilder cb,
                                        String visibility) {
        if ("all".equals(visibility)) {
            return;
        }
        if ("public".equals(visibility)) {
            predicates.add(publishedPredicate(root, cb));
            predicates.add(cb.or(
                    cb.isNull(root.get("category")),
                    cb.isNull(root.get("category").get("visibility")),
                    cb.equal(root.get("category").get("visibility"), CategoryVisibility.PUBLIC)
            ));
            return;
        }
        if ("private".equals(visibility)) {
            predicates.add(cb.equal(root.get("category").get("visibility"), CategoryVisibility.PRIVATE));
            return;
        }
        if ("unpublished".equals(visibility)) {
            predicates.add(cb.isFalse(root.get("published")));
            return;
        }
        if ("uncategorized".equals(visibility)) {
            predicates.add(cb.isNull(root.get("category")));
        }
    }

    private void addCategoryPredicate(List<Predicate> predicates,
                                      Root<Board> root,
                                      jakarta.persistence.criteria.CriteriaBuilder cb,
                                      String categoryKey,
                                      boolean activeOnly) {
        if (categoryKey == null) {
            return;
        }
        Category category = categoryRepository.findByKey(categoryKey)
                .filter(it -> !activeOnly || Boolean.TRUE.equals(it.getActive()))
                .orElseThrow(() -> new NotFoundException("글머리를 찾을 수 없습니다."));
        predicates.add(cb.equal(root.get("category"), category));
    }

    private void addKeywordPredicate(List<Predicate> predicates,
                                     Root<Board> root,
                                     jakarta.persistence.criteria.CriteriaQuery<?> query,
                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                     String keyword,
                                     String searchType) {
        if (keyword == null) {
            return;
        }

        String likeKeyword = "%" + keyword.toLowerCase() + "%";
        if ("title".equals(searchType)) {
            predicates.add(like(cb, root.get("title"), likeKeyword));
            return;
        }
        if ("content".equals(searchType)) {
            predicates.add(rawLike(cb, contentExpression(root, cb), "%" + keyword + "%"));
            return;
        }
        if ("author".equals(searchType)) {
            predicates.add(cb.or(
                    like(cb, root.get("user").get("name"), likeKeyword),
                    like(cb, root.get("user").get("email"), likeKeyword)
            ));
            return;
        }
        if ("comment".equals(searchType)) {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Comment> comment = subquery.from(Comment.class);
            subquery.select(comment.get("commentId"));
            subquery.where(
                    cb.equal(comment.get("post").get("boardId"), root.get("boardId")),
                    cb.or(cb.isNull(comment.get("deleted")), cb.isFalse(comment.get("deleted"))),
                    rawLike(cb, comment.get("content"), "%" + keyword + "%")
            );
            predicates.add(cb.exists(subquery));
            return;
        }

        predicates.add(cb.or(
                like(cb, root.get("title"), likeKeyword),
                rawLike(cb, contentExpression(root, cb), "%" + keyword + "%")
        ));
    }

    private Expression<String> contentExpression(Root<Board> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.coalesce(root.get("contentMarkdown"), root.get("content"));
    }

    private Predicate like(jakarta.persistence.criteria.CriteriaBuilder cb, Expression<String> expression, String likeKeyword) {
        return cb.like(cb.lower(cb.coalesce(expression, "")), likeKeyword);
    }

    private Predicate rawLike(jakarta.persistence.criteria.CriteriaBuilder cb, Expression<String> expression, String likeKeyword) {
        return cb.like(expression, likeKeyword);
    }

    private Pageable sortedPageable(Pageable pageable, String sort, int defaultSize) {
        String effectiveSort = effectiveSort(sort);
        int page = pageable == null ? 0 : Math.max(pageable.getPageNumber(), 0);
        int size = pageable == null ? defaultSize : pageable.getPageSize();
        size = Math.max(1, Math.min(size, 100));
        Sort.Direction direction = "oldest".equals(effectiveSort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort resolvedSort = Sort.by(direction, "createdAt").and(Sort.by(direction, "boardId"));
        return PageRequest.of(page, size, resolvedSort);
    }

    private void validatePublicCondition(BoardSearchCondition condition) {
        effectiveSearchType(condition.getSearchType());
        effectiveSort(condition.getSort());
    }

    private void validateAdminCondition(BoardSearchCondition condition) {
        effectiveVisibility(condition.getVisibility());
        validatePublicCondition(condition);
    }

    private String effectiveVisibility(String visibility) {
        String value = visibility == null ? "all" : visibility.toLowerCase();
        if (!Set.of("all", "public", "private", "unpublished", "uncategorized").contains(value)) {
            throw new BadRequestException("지원하지 않는 visibility 값입니다.");
        }
        return value;
    }

    private String effectiveSearchType(String searchType) {
        String value = searchType == null ? "title_content" : searchType.toLowerCase();
        if (!Set.of("title_content", "title", "content", "comment", "author").contains(value)) {
            throw new BadRequestException("지원하지 않는 searchType 값입니다.");
        }
        return value;
    }

    private String effectiveSort(String sort) {
        String value = sort == null ? "latest" : sort.toLowerCase();
        if (!Set.of("latest", "oldest").contains(value)) {
            throw new BadRequestException("지원하지 않는 sort 값입니다.");
        }
        return value;
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

    private BoardDto.Response toListResponse(Board board, User viewer) {
        if (isPrivateCategory(board) && !authorizationService.isAdmin(viewer)) {
            return BoardDto.Response.masked(board);
        }
        return toResponse(board);
    }
}
