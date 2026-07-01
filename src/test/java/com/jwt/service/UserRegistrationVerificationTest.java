package com.jwt.service;

import com.jwt.dto.RegistrationDto;
import com.jwt.emum.EmailVerificationResult;
import com.jwt.entity.User;
import com.jwt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationVerificationTest {

    @Mock
    UserRepository userRepository;

    @Mock
    EmailService emailService;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    AuthorizationService authorizationService;

    @InjectMocks
    UserService userService;

    RegistrationDto request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "emailVerificationEnabled", true);
        request = new RegistrationDto();
        request.setName("Member");
        request.setEmail("member@example.com");
        request.setPassword("password");
        request.setVerificationCode("123456");
    }

    @Test
    void registrationConsumesRegistrationVerificationCodeWhenEnabled() {
        when(emailService.verifyEmailCodeWithDetails(request.getEmail(), request.getVerificationCode()))
                .thenReturn(EmailVerificationResult.SUCCESS);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.registerUser(request);

        assertThat(saved.getEmail()).isEqualTo(request.getEmail());
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        verify(userRepository).save(saved);
    }

    @Test
    void registrationRejectsInvalidVerificationCodeWhenEnabled() {
        when(emailService.verifyEmailCodeWithDetails(request.getEmail(), request.getVerificationCode()))
                .thenReturn(EmailVerificationResult.INVALID_CODE);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("인증 코드가 일치하지 않습니다.");

        verify(userRepository, never()).save(any(User.class));
    }
}
