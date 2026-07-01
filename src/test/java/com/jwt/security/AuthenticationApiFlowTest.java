package com.jwt.security;

import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void refreshApiRotatesStoredTokenAndReturnsNewCookies() throws Exception {
        User user = userRepository.saveAndFlush(activeUser("refresh-flow@example.com"));
        MvcResult loginResult = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"refresh-flow@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie currentRefreshCookie = loginResult.getResponse().getCookie("jwt_refresh_token");
        assertThat(currentRefreshCookie).isNotNull();
        when(tokenRedisService.rotateRefreshToken(
                eq(String.valueOf(user.getUserId())),
                eq(currentRefreshCookie.getValue()),
                anyString(),
                anyLong()
        )).thenReturn(true, false);

        MvcResult refreshResult = mockMvc.perform(post("/api/user/token/refresh")
                        .cookie(currentRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("jwt_token="))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("jwt_refresh_token="))))
                .andReturn();

        Cookie replacementRefreshCookie = refreshResult.getResponse().getCookie("jwt_refresh_token");
        assertThat(replacementRefreshCookie).isNotNull();
        assertThat(replacementRefreshCookie.getValue()).isNotEqualTo(currentRefreshCookie.getValue());
        verify(tokenRedisService).rotateRefreshToken(
                eq(String.valueOf(user.getUserId())),
                eq(currentRefreshCookie.getValue()),
                eq(replacementRefreshCookie.getValue()),
                anyLong()
        );

        mockMvc.perform(post("/api/user/token/refresh").cookie(currentRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginReturnUrlAllowsOnlyLocalPaths() throws Exception {
        userRepository.saveAndFlush(activeUser("return-url@example.com"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "return-url@example.com")
                        .param("password", "password")
                        .param("returnUrl", "/posts?page=2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts?page=2"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "return-url@example.com")
                        .param("password", "password")
                        .param("returnUrl", "https://attacker.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts"));
    }

    @Test
    void logoutWithRefreshCookieRevokesRedisSessionWithoutAccessToken() throws Exception {
        User user = userRepository.saveAndFlush(activeUser("logout-refresh@example.com"));
        MvcResult loginResult = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"logout-refresh@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("jwt_refresh_token");
        assertThat(refreshCookie).isNotNull();
        when(tokenRedisService.deleteRefreshTokenIfMatches(
                String.valueOf(user.getUserId()), refreshCookie.getValue())).thenReturn(true);

        MvcResult logoutResult = mockMvc.perform(post("/api/user/logout").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        verify(tokenRedisService).deleteRefreshTokenIfMatches(
                String.valueOf(user.getUserId()), refreshCookie.getValue());
        assertThat(logoutResult.getResponse().getCookie("jwt_token").getMaxAge()).isZero();
        assertThat(logoutResult.getResponse().getCookie("jwt_refresh_token").getMaxAge()).isZero();
    }

    @Test
    void loginFailsClosedWhenRedisTokenStoreIsUnavailable() throws Exception {
        User user = userRepository.saveAndFlush(activeUser("redis-down@example.com"));
        doThrow(new IllegalStateException("인증 저장소를 사용할 수 없습니다."))
                .when(tokenRedisService)
                .saveRefreshToken(eq(String.valueOf(user.getUserId())), anyString(), anyLong());

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"redis-down@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(header().doesNotExist("Set-Cookie"));
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
