package com.jwt.security;

import com.jwt.dto.BoardDto;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.repository.UserRepository;
import com.jwt.service.BoardService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ThymeleafRenderSmokeTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    BoardService boardService;

    @Value("${jwt.access-cookie-name}")
    String accessCookieName;

    @Test
    void publicPagesRender() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk());
    }

    @Test
    void postPagesRender() throws Exception {
        User admin = userRepository.saveAndFlush(user("render-post-admin@example.com", UserRole.ADMIN));
        BoardDto.Response post = boardService.createBoard(new BoardDto.Request("Render Post", "Body"), admin);

        mockMvc.perform(get("/posts/{postId}", post.getPostId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/posts/{postId}/edit", post.getPostId()).cookie(accessCookie(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void adminPagesRender() throws Exception {
        User admin = userRepository.saveAndFlush(user("render-admin@example.com", UserRole.ADMIN));
        Cookie cookie = accessCookie(admin);

        mockMvc.perform(get("/admin").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/posts").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/posts/new").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/categories").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/users").cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/comments").cookie(cookie))
                .andExpect(status().isOk());
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
}
