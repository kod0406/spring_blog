package com.jwt.service;

import com.jwt.emum.EmailVerificationResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceVerificationTest {

    @Mock
    JavaMailSender javaMailSender;

    @Mock
    RedisTemplate<String, String> redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(javaMailSender, redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void registrationAndPasswordResetCodesUseDifferentNamespaces() {
        when(javaMailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));

        emailService.sendVerificationEmail("member@example.com");
        emailService.sendPasswordResetVerificationEmail("member@example.com");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.times(2)).set(
                keyCaptor.capture(),
                any(String.class),
                eq(5L),
                eq(TimeUnit.MINUTES)
        );

        List<String> keys = keyCaptor.getAllValues();
        assertThat(keys).containsExactly(
                "email_verification:registration:member@example.com",
                "email_verification:password-reset:member@example.com"
        );
    }

    @Test
    void successfulPasswordResetVerificationConsumesOnlyResetCode() {
        String resetKey = "email_verification:password-reset:member@example.com";
        when(valueOperations.get(resetKey)).thenReturn("123456");

        EmailVerificationResult result = emailService.verifyPasswordResetCode("member@example.com", "123456");

        assertThat(result).isEqualTo(EmailVerificationResult.SUCCESS);
        verify(redisTemplate).delete(resetKey);
    }
}
