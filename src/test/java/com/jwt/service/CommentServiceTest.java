package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.dto.CategoryDto;
import com.jwt.dto.CommentDto;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.Comment;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.CommentRepository;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentServiceTest {

    @Autowired
    BoardService boardService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    CommentService commentService;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void commentTreeSupportsArbitraryDepthAndSoftDeleteKeepsTree() {
        User admin = userRepository.save(user("comment-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        User member = userRepository.save(user("comment-member@example.com", "Member", UserRole.USER, UserStatus.ACTIVE));
        BoardDto.Response post = boardService.createBoard(new BoardDto.Request("글", "본문"), admin);

        CommentDto.Response root = commentService.create(post.getPostId(), comment("root"), member);
        CommentDto.Response child = commentService.reply(root.getCommentId(), comment("child"), member);
        commentService.reply(child.getCommentId(), comment("grandchild"), member);
        commentService.delete(child.getCommentId(), member);

        Comment deleted = commentRepository.findById(child.getCommentId()).orElseThrow();
        List<CommentDto.Response> tree = commentService.getTree(post.getPostId(), member);

        assertThat(deleted.getDeleted()).isTrue();
        assertThat(deleted.getContent()).isEmpty();
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getContent()).isEqualTo("삭제된 댓글입니다.");
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getDepth()).isEqualTo(2);
    }

    @Test
    void privatePostCommentsOnlyAdminCanWrite() {
        User admin = userRepository.save(user("private-comment-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        User member = userRepository.save(user("private-comment-member@example.com", "Member", UserRole.USER, UserStatus.ACTIVE));
        CategoryDto.Response privateCategory = categoryService.create(category("comment-private", "개인", CategoryVisibility.PRIVATE), admin);
        BoardDto.Request request = new BoardDto.Request("개인 글", "본문");
        request.setCategoryKey(privateCategory.getKey());
        BoardDto.Response post = boardService.createBoard(request, admin);

        assertThatThrownBy(() -> commentService.create(post.getPostId(), comment("member"), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("글을 찾을 수 없습니다");
        assertThatThrownBy(() -> commentService.getTree(post.getPostId(), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("글을 찾을 수 없습니다");

        CommentDto.Response adminComment = commentService.create(post.getPostId(), comment("admin"), admin);
        assertThatThrownBy(() -> commentService.reply(adminComment.getCommentId(), comment("member reply"), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("글을 찾을 수 없습니다");

        CommentDto.Response adminReply = commentService.reply(adminComment.getCommentId(), comment("admin reply"), admin);

        assertThat(adminComment.getContent()).isEqualTo("admin");
        assertThat(adminReply.getContent()).isEqualTo("admin reply");
        assertThat(commentService.getTree(post.getPostId(), admin)).hasSize(1);
    }

    private CategoryDto.Request category(String key, String displayName, CategoryVisibility visibility) {
        CategoryDto.Request request = new CategoryDto.Request();
        request.setKey(key);
        request.setDisplayName(displayName);
        request.setActive(true);
        request.setVisibility(visibility);
        return request;
    }

    private CommentDto.Request comment(String content) {
        CommentDto.Request request = new CommentDto.Request();
        request.setContent(content);
        return request;
    }

    private User user(String email, String name, UserRole role, UserStatus status) {
        return User.builder()
                .email(email)
                .name(name)
                .password("{noop}password")
                .role(role)
                .status(status)
                .build();
    }
}
