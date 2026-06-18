package com.jwt.service;

import com.jwt.dto.BoardDto;
import com.jwt.dto.CategoryDto;
import com.jwt.entity.CategoryVisibility;
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
        User admin = userRepository.save(user("board-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));

        BoardDto.Response created = boardService.createBoard(new BoardDto.Request("첫 글", "본문"), admin);
        BoardDto.Response updated = boardService.updateBoard(created.getPostId(), new BoardDto.Request("수정 글", "수정 본문"), admin);
        boardService.deleteBoard(created.getPostId(), admin);

        assertThat(created.getAuthorId()).isEqualTo(admin.getUserId());
        assertThat(updated.getTitle()).isEqualTo("수정 글");
        assertThat(boardRepository.findById(created.getPostId())).isEmpty();
    }

    @Test
    void activeUserCannotCreateUpdateOrDeletePost() {
        User admin = userRepository.save(user("board-admin-2@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        User member = userRepository.save(user("board-member@example.com", "Member", UserRole.USER, UserStatus.ACTIVE));
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
    }

    @Test
    void privateCategoryPostsAreVisibleOnlyToAdmin() {
        User admin = userRepository.save(user("private-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        User member = userRepository.save(user("private-member@example.com", "Member", UserRole.USER, UserStatus.ACTIVE));
        CategoryDto.Response publicCategory = categoryService.create(category("public-test", "공지", CategoryVisibility.PUBLIC), admin);
        CategoryDto.Response privateCategory = categoryService.create(category("private-test", "개인", CategoryVisibility.PRIVATE), admin);

        BoardDto.Request publicRequest = new BoardDto.Request("공개 글", "본문");
        publicRequest.setCategoryKey(publicCategory.getKey());
        BoardDto.Response publicPost = boardService.createBoard(publicRequest, admin);

        BoardDto.Request privateRequest = new BoardDto.Request("개인 글", "본문");
        privateRequest.setCategoryKey(privateCategory.getKey());
        BoardDto.Response privatePost = boardService.createBoard(privateRequest, admin);

        assertThat(boardService.getPublicPosts(null, PageRequest.of(0, 10)).getContent())
                .extracting(BoardDto.Response::getPostId)
                .contains(publicPost.getPostId())
                .doesNotContain(privatePost.getPostId());
        assertThat(boardService.getPosts(null, PageRequest.of(0, 10), admin).getContent())
                .extracting(BoardDto.Response::getPostId)
                .doesNotContain(privatePost.getPostId());
        assertThat(boardService.getAdminPosts("all", null, PageRequest.of(0, 10), admin).getContent())
                .extracting(BoardDto.Response::getPostId)
                .contains(privatePost.getPostId());
        assertThatThrownBy(() -> boardService.getBoardById(privatePost.getPostId(), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("글을 찾을 수 없습니다");
    }

    @Test
    void categoryVisibilityChangeImmediatelyChangesPublicExposure() {
        User admin = userRepository.save(user("flip-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        CategoryDto.Response category = categoryService.create(category("flip", "전환", CategoryVisibility.PUBLIC), admin);
        BoardDto.Request request = new BoardDto.Request("전환 글", "본문");
        request.setCategoryKey(category.getKey());
        BoardDto.Response post = boardService.createBoard(request, admin);

        assertThat(boardService.getPublicPosts(null, PageRequest.of(0, 10)).getContent())
                .extracting(BoardDto.Response::getPostId)
                .contains(post.getPostId());

        CategoryDto.Request update = category(category.getKey(), "전환", CategoryVisibility.PRIVATE);
        categoryService.update(category.getCategoryId(), update, admin);

        assertThat(boardService.getPublicPosts(null, PageRequest.of(0, 10)).getContent())
                .extracting(BoardDto.Response::getPostId)
                .doesNotContain(post.getPostId());
        assertThat(boardService.getPosts(null, PageRequest.of(0, 10), admin).getContent())
                .extracting(BoardDto.Response::getPostId)
                .doesNotContain(post.getPostId());
        assertThat(boardService.getAdminPosts("all", null, PageRequest.of(0, 10), admin).getContent())
                .extracting(BoardDto.Response::getPostId)
                .contains(post.getPostId());
    }

    @Test
    void unpublishedPostIsAdminOnly() {
        User admin = userRepository.save(user("unpublished-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        BoardDto.Request request = new BoardDto.Request("숨긴 글", "본문");
        request.setPublished(false);
        BoardDto.Response post = boardService.createBoard(request, admin);

        assertThat(boardService.getPublicPosts(null, PageRequest.of(0, 10)).getContent())
                .extracting(BoardDto.Response::getPostId)
                .doesNotContain(post.getPostId());
        assertThat(boardService.getAdminPosts("unpublished", null, PageRequest.of(0, 10), admin).getContent())
                .extracting(BoardDto.Response::getPostId)
                .contains(post.getPostId());
        assertThatThrownBy(() -> boardService.getBoardById(post.getPostId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("글을 찾을 수 없습니다");
    }

    @Test
    void deletingCategoryKeepsPostsAsUncategorized() {
        User admin = userRepository.save(user("category-admin@example.com", "Admin", UserRole.ADMIN, UserStatus.ACTIVE));
        CategoryDto.Response publicCategory = categoryService.create(category("category-public", "공지", CategoryVisibility.PUBLIC), admin);
        categoryService.create(category("category-dev", "개발", CategoryVisibility.PUBLIC), admin);

        BoardDto.Request request = new BoardDto.Request("공지 글", "본문");
        request.setCategoryKey(publicCategory.getKey());
        BoardDto.Response post = boardService.createBoard(request, admin);

        categoryService.delete(publicCategory.getCategoryId(), admin);

        BoardDto.Response uncategorized = boardService.getBoardById(post.getPostId(), null);
        assertThat(uncategorized.getCategoryKey()).isNull();
        assertThat(uncategorized.getCategoryDisplayName()).isEqualTo("미분류");
    }

    private CategoryDto.Request category(String key, String displayName, CategoryVisibility visibility) {
        CategoryDto.Request request = new CategoryDto.Request();
        request.setKey(key);
        request.setDisplayName(displayName);
        request.setActive(true);
        request.setVisibility(visibility);
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
