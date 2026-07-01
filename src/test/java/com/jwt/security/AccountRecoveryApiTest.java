package com.jwt.security;

import com.jwt.emum.EmailVerificationResult;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import com.jwt.service.EmailService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountRecoveryApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailService emailService;

    @MockitoBean
    TokenRedisService tokenRedisService;

    @Test
    void resetCodeRequestReturnsSameResponseForKnownAndUnknownAccounts() throws Exception {
        userRepository.saveAndFlush(user("recovery-known@example.com"));

        requestCode("recovery-known@example.com");
        requestCode("recovery-unknown@example.com");

        verify(emailService).sendPasswordResetVerificationEmail("recovery-known@example.com");
        verify(emailService, never()).sendPasswordResetVerificationEmail("recovery-unknown@example.com");
    }

    @Test
    void passwordResetRequiresPurposeSpecificVerificationCode() throws Exception {
        User user = userRepository.saveAndFlush(user("recovery-reset@example.com"));
        when(emailService.verifyPasswordResetCode("recovery-reset@example.com", "123456"))
                .thenReturn(EmailVerificationResult.SUCCESS);

        mockMvc.perform(post("/api/user/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "recovery-reset@example.com",
                                  "verificationCode": "123456",
                                  "newPassword": "new-password",
                                  "confirmPassword": "new-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(passwordEncoder.matches("new-password", user.getPassword())).isTrue();
        verify(tokenRedisService).deleteRefreshToken(String.valueOf(user.getUserId()));
    }

    @Test
    void passwordResetWithoutVerificationCodeIsRejected() throws Exception {
        User user = userRepository.saveAndFlush(user("recovery-no-code@example.com"));

        mockMvc.perform(post("/api/user/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "recovery-no-code@example.com",
                                  "newPassword": "new-password",
                                  "confirmPassword": "new-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(passwordEncoder.matches("old-password", user.getPassword())).isTrue();
    }

    private void requestCode(String email) throws Exception {
        mockMvc.perform(post("/api/user/reset-password/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("계정이 존재하면 인증 코드가 이메일로 발송됩니다."));
    }

    private User user(String email) {
        return User.builder()
                .email(email)
                .name("Member")
                .password(passwordEncoder.encode("old-password"))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
