package com.jwt.service;

import com.jwt.dto.CommentDto;
import com.jwt.entity.Board;
import com.jwt.entity.Comment;
import com.jwt.entity.User;
import com.jwt.exception.BadRequestException;
import com.jwt.exception.ForbiddenException;
import com.jwt.exception.NotFoundException;
import com.jwt.repository.CommentRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final BoardService boardService;
    private final AuthorizationService authorizationService;
    private final CommentDtoMapper commentDtoMapper;

    @Transactional(readOnly = true)
    public List<CommentDto.Response> getTree(Long postId, User viewer) {
        Board post = boardService.getReadablePostEntity(postId, viewer);
        return toTree(commentRepository.findAllByPostOrderByCreatedAtAsc(post));
    }

    @Transactional(readOnly = true)
    public List<CommentDto.Response> getTree(Long postId) {
        return getTree(postId, null);
    }

    @Transactional(readOnly = true)
    public List<CommentDto.Response> getAllComments(User user) {
        return getAllComments(user, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<CommentDto.Response> getAllComments(User user, Long postId, String author, Boolean deleted, String keyword) {
        authorizationService.requireAdmin(user);
        return commentRepository.findAll(adminCommentSpecification(postId, author, deleted, keyword), Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(comment -> commentDtoMapper.toResponse(comment, depthOf(comment)))
                .toList();
    }

    @Transactional
    public CommentDto.Response create(Long postId, CommentDto.Request request, User user) {
        validateRequest(request);
        Board post = boardService.getReadablePostEntity(postId, user);
        requireCommentPermission(post, user);

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(user);
        comment.setContent(request.getContent().trim());
        return commentDtoMapper.toResponse(commentRepository.save(comment), 0);
    }

    @Transactional
    public CommentDto.Response reply(Long parentCommentId, CommentDto.Request request, User user) {
        validateRequest(request);

        Comment parent = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다."));
        Board post = boardService.getReadablePostEntity(parent.getPost().getBoardId(), user);
        requireCommentPermission(post, user);

        Comment comment = new Comment();
        comment.setPost(parent.getPost());
        comment.setAuthor(user);
        comment.setParent(parent);
        comment.setContent(request.getContent().trim());
        return commentDtoMapper.toResponse(commentRepository.save(comment), depthOf(parent) + 1);
    }

    @Transactional
    public void delete(Long commentId, User user) {
        authorizationService.requireActiveUser(user);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다."));

        boolean owner = comment.getAuthor().getUserId() == user.getUserId();
        if (!owner && !authorizationService.isAdmin(user)) {
            throw new ForbiddenException("댓글 삭제 권한이 없습니다.");
        }

        softDelete(comment);
    }

    @Transactional
    public void adminDelete(Long commentId, User user) {
        authorizationService.requireAdmin(user);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("댓글을 찾을 수 없습니다."));
        softDelete(comment);
    }

    private List<CommentDto.Response> toTree(List<Comment> comments) {
        Map<Long, CommentDto.Response> responses = new LinkedHashMap<>();
        List<CommentDto.Response> roots = new ArrayList<>();

        for (Comment comment : comments) {
            responses.put(comment.getCommentId(), commentDtoMapper.toResponse(comment, depthOf(comment)));
        }

        for (Comment comment : comments) {
            CommentDto.Response response = responses.get(comment.getCommentId());
            if (comment.getParent() == null) {
                roots.add(response);
                continue;
            }
            CommentDto.Response parent = responses.get(comment.getParent().getCommentId());
            if (parent == null) {
                roots.add(response);
            } else {
                parent.getChildren().add(response);
            }
        }
        return roots;
    }

    private void requireCommentPermission(Board post, User user) {
        if (!boardService.canComment(post, user)) {
            if (boardService.isPrivateCategory(post)) {
                throw new NotFoundException("글을 찾을 수 없습니다.");
            }
            authorizationService.requireActiveUser(user);
        }
    }

    private void softDelete(Comment comment) {
        comment.setDeleted(true);
        comment.setContent("");
    }

    private void validateRequest(CommentDto.Request request) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BadRequestException("댓글 내용을 입력해 주세요.");
        }
        if (request.getContent().trim().length() > 2000) {
            throw new BadRequestException("댓글은 2000자 이하로 입력해 주세요.");
        }
    }

    private int depthOf(Comment comment) {
        int depth = 0;
        Comment cursor = comment.getParent();
        while (cursor != null) {
            depth++;
            cursor = cursor.getParent();
        }
        return depth;
    }

    private Specification<Comment> adminCommentSpecification(Long postId, String author, Boolean deleted, String keyword) {
        return (root, query, cb) -> {
            root.fetch("post", JoinType.LEFT);
            root.fetch("author", JoinType.LEFT);
            root.fetch("parent", JoinType.LEFT);
            query.distinct(true);

            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (postId != null) {
                predicates.add(cb.equal(root.get("post").get("boardId"), postId));
            }
            if (author != null && !author.isBlank()) {
                String needle = "%" + author.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("author").get("name"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.get("author").get("email"), "")), needle)
                ));
            }
            if (deleted != null) {
                predicates.add(cb.equal(cb.coalesce(root.get("deleted"), false), deleted));
            }
            if (keyword != null && !keyword.isBlank()) {
                String needle = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("content"), "")), needle),
                        cb.like(cb.lower(cb.coalesce(root.get("post").get("title"), "")), needle)
                ));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
