package com.jwt.security;

import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationApiFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    TokenRedisService tokenRedisService;

    @Test
    void apiRegistrationCreatesPendingUser() throws Exception {
        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Member",
                                  "email": "api-register@example.com",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User saved = userRepository.findByEmail("api-register@example.com");
        assertThat(saved).isNotNull();
        assertThat(saved.getStatusEnum()).isEqualTo(UserStatus.PENDING);
        assertThat(passwordEncoder.matches("password", saved.getPassword())).isTrue();
    }

    @Test
    void activeUserCanLoginThroughApiAndWeb() throws Exception {
        userRepository.saveAndFlush(activeUser("login-flow@example.com"));

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login-flow@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("login-flow@example.com"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("jwt_token="))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("jwt_refresh_token="))));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "login-flow@example.com")
                        .param("password", "password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("jwt_token="))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("jwt_refresh_token="))));
    }

    private User activeUser(String email) {
        return User.builder()
                .email(email)
                .name("Member")
                .password(passwordEncoder.encode("password"))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
