package com.jwt.service;

import com.jwt.dto.BoardSearchCondition;
import com.jwt.entity.Board;
import com.jwt.entity.Category;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.Comment;
import com.jwt.exception.BadRequestException;
import com.jwt.exception.NotFoundException;
import com.jwt.repository.CategoryRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BoardSearchSpecificationFactory {
    private final CategoryRepository categoryRepository;

    public Specification<Board> publicSpecification(BoardSearchCondition condition) {
        validatePublicCondition(condition);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(publishedPredicate(root, cb));
            addCategoryPredicate(predicates, root, cb, condition.getCategory(), true);
            addKeywordPredicate(predicates, root, query, cb, condition.getKeyword(), effectiveSearchType(condition.getSearchType()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public Specification<Board> adminSpecification(BoardSearchCondition condition) {
        validateAdminCondition(condition);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addVisibilityPredicate(predicates, root, cb, effectiveVisibility(condition.getVisibility()));
            addCategoryPredicate(predicates, root, cb, condition.getCategory(), false);
            addKeywordPredicate(predicates, root, query, cb, condition.getKeyword(), effectiveSearchType(condition.getSearchType()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public String effectiveSort(String sort) {
        String value = sort == null ? "latest" : sort.toLowerCase();
        if (!Set.of("latest", "oldest").contains(value)) {
            throw new BadRequestException("지원하지 않는 sort 값입니다.");
        }
        return value;
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
}
