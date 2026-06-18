package com.jwt.security;

import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.jwt.JwtTokenProvider;
import com.jwt.repository.UserRepository;
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
class SecurityAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    UserRepository userRepository;

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
