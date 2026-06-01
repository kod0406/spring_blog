package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.dto.CommentDto;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentServiceTest {

    @Autowired
    BoardService boardService;

    @Autowired
    CommentService commentService;

    @Autowired
    UserRepository userRepository;

    @Test
    void commentTreeSupportsArbitraryDepth() {
        User admin = userRepository.save(user("admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        User member = userRepository.save(user("member@example.com", "Member", UserRole.USER, UserStatus.ACTIVE));
        BoardDto.Response post = boardService.createBoard(new BoardDto.Request("글", "본문"), admin);

        CommentDto.Response root = commentService.create(post.getPostId(), comment("root"), member);
        CommentDto.Response child = commentService.reply(root.getCommentId(), comment("child"), member);
        commentService.reply(child.getCommentId(), comment("grandchild"), member);

        List<CommentDto.Response> tree = commentService.getTree(post.getPostId());

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getDepth()).isZero();
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getDepth()).isEqualTo(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getDepth()).isEqualTo(2);
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
