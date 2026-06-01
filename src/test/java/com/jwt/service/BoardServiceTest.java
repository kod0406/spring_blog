package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.dto.CategoryDto;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.BoardRepository;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BoardServiceTest {

    @Autowired
    BoardService boardService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    BoardRepository boardRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void adminCanCreateUpdateAndDeletePost() {
        User admin = userRepository.save(user("admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));

        BoardDto.Response created = boardService.createBoard(new BoardDto.Request("첫 글", "본문"), admin);
        BoardDto.Response updated = boardService.updateBoard(created.getPostId(), new BoardDto.Request("수정 글", "수정 본문"), admin);
        boardService.deleteBoard(created.getPostId(), admin);

        assertThat(created.getAuthorId()).isEqualTo(admin.getUserId());
        assertThat(updated.getTitle()).isEqualTo("수정 글");
        assertThat(boardRepository.findById(created.getPostId())).isEmpty();
    }

    @Test
    void activeUserCannotCreateUpdateOrDeletePost() {
        User admin = userRepository.save(user("admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        User member = userRepository.save(user("member@example.com", "Member", UserRole.USER, UserStatus.ACTIVE));
        BoardDto.Response created = boardService.createBoard(new BoardDto.Request("첫 글", "본문"), admin);

        assertThatThrownBy(() -> boardService.createBoard(new BoardDto.Request("침범", "내용"), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 권한");

        assertThatThrownBy(() -> boardService.updateBoard(created.getPostId(), new BoardDto.Request("침범", "내용"), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 권한");

        assertThatThrownBy(() -> boardService.deleteBoard(created.getPostId(), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자 권한");

        assertThat(boardRepository.findById(created.getPostId())).isPresent();
    }

    @Test
    void deletingCategoryKeepsPostsAsUncategorized() {
        User admin = userRepository.save(user("admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        CategoryDto.Response publicCategory = categoryService.create(category("public", "공지"), admin);
        categoryService.create(category("dev", "개발"), admin);

        BoardDto.Request request = new BoardDto.Request("공지 글", "본문");
        request.setCategoryKey(publicCategory.getKey());
        BoardDto.Response post = boardService.createBoard(request, admin);

        categoryService.delete(publicCategory.getCategoryId(), admin);

        BoardDto.Response uncategorized = boardService.getBoardById(post.getPostId());
        assertThat(uncategorized.getCategoryKey()).isNull();
        assertThat(uncategorized.getCategoryDisplayName()).isEqualTo("미분류");
        assertThat(boardService.getPublicPosts(null, PageRequest.of(0, 10)).getContent())
                .extracting(BoardDto.Response::getPostId)
                .contains(post.getPostId());
        assertThat(boardService.getPublicPosts("dev", PageRequest.of(0, 10)).getContent())
                .extracting(BoardDto.Response::getPostId)
                .doesNotContain(post.getPostId());
    }

    private CategoryDto.Request category(String key, String displayName) {
        CategoryDto.Request request = new CategoryDto.Request();
        request.setKey(key);
        request.setDisplayName(displayName);
        request.setActive(true);
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
