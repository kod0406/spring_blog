package com.jwt.security;

import com.jwt.dto.BoardDto;
import com.jwt.dto.CategoryDto;
import com.jwt.entity.CategoryVisibility;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.repository.UserRepository;
import com.jwt.service.BoardService;
import com.jwt.service.CategoryService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BoardService boardService;

    @Autowired
    CategoryService categoryService;

    @Value("${jwt.access-cookie-name}")
    String accessCookieName;

    @Test
    void anonymousCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeUserCannotAccessAdminApi() throws Exception {
        User member = userRepository.saveAndFlush(user("security-user@example.com", UserRole.USER));

        mockMvc.perform(get("/api/admin/categories").cookie(accessCookie(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void activeAdminCanAccessAdminApi() throws Exception {
        User admin = userRepository.saveAndFlush(user("security-admin@example.com", UserRole.ADMIN));

        mockMvc.perform(get("/api/admin/categories").cookie(accessCookie(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void webAdminPagesRequireAdmin() throws Exception {
        User member = userRepository.saveAndFlush(user("security-web-user@example.com", UserRole.USER));
        User admin = userRepository.saveAndFlush(user("security-web-admin@example.com", UserRole.ADMIN));

        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/posts").cookie(accessCookie(member)))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/posts").cookie(accessCookie(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void privatePostDetailAndCommentsRequireAdmin() throws Exception {
        User admin = userRepository.saveAndFlush(user("security-private-admin@example.com", UserRole.ADMIN));
        User member = userRepository.saveAndFlush(user("security-private-user@example.com", UserRole.USER));
        CategoryDto.Response category = categoryService.create(category("security-private", "개인", CategoryVisibility.PRIVATE), admin);
        BoardDto.Request request = new BoardDto.Request("개인 글", "본문");
        request.setCategoryKey(category.getKey());
        BoardDto.Response post = boardService.createBoard(request, admin);

        mockMvc.perform(get("/api/posts/{postId}", post.getPostId()).cookie(accessCookie(member)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/{postId}/comments", post.getPostId()).cookie(accessCookie(member)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/{postId}/comments", post.getPostId())
                        .cookie(accessCookie(member))
                        .contentType("application/json")
                        .content("{\"content\":\"member\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/{postId}", post.getPostId()).cookie(accessCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(post.getPostId()));
    }

    @Test
    void publicListMasksPrivatePostsForNonAdmin() throws Exception {
        User admin = userRepository.saveAndFlush(user("security-list-admin@example.com", UserRole.ADMIN));
        User member = userRepository.saveAndFlush(user("security-list-user@example.com", UserRole.USER));
        CategoryDto.Response category = categoryService.create(category("security-list-private", "개인", CategoryVisibility.PRIVATE), admin);
        BoardDto.Request request = new BoardDto.Request("목록 개인 글", "본문");
        request.setCategoryKey(category.getKey());
        boardService.createBoard(request, admin);

        mockMvc.perform(get("/api/posts").cookie(accessCookie(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].masked").value(true))
                .andExpect(jsonPath("$.data.content[0].postId").value(nullValue()));
    }

    private Cookie accessCookie(User user) {
        Cookie cookie = new Cookie(accessCookieName, jwtTokenProvider.createAccessToken(String.valueOf(user.getUserId()), user.getRole()));
        cookie.setPath("/");
        return cookie;
    }

    private User user(String email, UserRole role) {
        return User.builder()
                .email(email)
                .name(email)
                .password("{noop}password")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private CategoryDto.Request category(String key, String displayName, CategoryVisibility visibility) {
        CategoryDto.Request request = new CategoryDto.Request();
        request.setKey(key);
        request.setDisplayName(displayName);
        request.setActive(true);
        request.setVisibility(visibility);
        return request;
    }
}
