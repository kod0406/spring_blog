package com.jwt.service;

import com.jwt.emum.EmailVerificationResult;
import com.jwt.entity.User;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountRecoveryServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    EmailService emailService;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    TokenRedisService tokenRedisService;

    @InjectMocks
    UserAccountRecoveryService recoveryService;

    User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(42L)
                .email("member@example.com")
                .password("encoded-old")
                .build();
    }

    @Test
    void passwordResetRequestDoesNotRevealUnknownAccount() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(null);

        recoveryService.requestPasswordReset("unknown@example.com");

        verify(emailService, never()).sendPasswordResetVerificationEmail("unknown@example.com");
    }

    @Test
    void passwordResetRequestSendsPurposeSpecificCodeForKnownAccount() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);

        recoveryService.requestPasswordReset(" member@example.com ");

        verify(emailService).sendPasswordResetVerificationEmail(user.getEmail());
    }

    @Test
    void passwordCannotChangeWithoutValidResetCode() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(emailService.verifyPasswordResetCode(user.getEmail(), "000000"))
                .thenReturn(EmailVerificationResult.INVALID_CODE);

        assertThatThrownBy(() -> recoveryService.resetPassword(
                user.getEmail(), "000000", "new-password", "new-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("인증 정보가 올바르지 않거나 만료되었습니다.");

        assertThat(user.getPassword()).isEqualTo("encoded-old");
        verify(tokenRedisService, never()).deleteRefreshToken("42");
    }

    @Test
    void validResetCodeChangesPasswordAndRevokesRefreshToken() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(emailService.verifyPasswordResetCode(user.getEmail(), "123456"))
                .thenReturn(EmailVerificationResult.SUCCESS);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        recoveryService.resetPassword(user.getEmail(), "123456", "new-password", "new-password");

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(tokenRedisService).deleteRefreshToken("42");
    }
}
