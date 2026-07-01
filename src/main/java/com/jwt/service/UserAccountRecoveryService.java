package com.jwt.service;

import com.jwt.emum.EmailVerificationResult;
import com.jwt.entity.User;
import com.jwt.redis.TokenRedisService;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountRecoveryService {

    private static final String INVALID_VERIFICATION_MESSAGE = "인증 정보가 올바르지 않거나 만료되었습니다.";

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TokenRedisService tokenRedisService;

    public void requestPasswordReset(String email) {
        String normalizedEmail = requireEmail(email);
        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            return;
        }

        try {
            emailService.sendPasswordResetVerificationEmail(normalizedEmail);
        } catch (RuntimeException e) {
            // 응답으로 계정 존재 여부가 노출되지 않도록 발송 실패도 동일하게 처리한다.
            log.error("비밀번호 재설정 인증 메일 발송 실패. email={}", normalizedEmail, e);
        }
    }

    @Transactional
    public void resetPassword(String email, String verificationCode, String newPassword, String confirmPassword) {
        String normalizedEmail = requireEmail(email);
        validatePasswords(newPassword, confirmPassword);
        if (verificationCode == null || verificationCode.isBlank()) {
            throw new IllegalArgumentException("이메일 인증 코드를 입력해 주세요.");
        }

        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            throw new IllegalArgumentException(INVALID_VERIFICATION_MESSAGE);
        }

        EmailVerificationResult result = emailService.verifyPasswordResetCode(
                normalizedEmail,
                verificationCode.trim()
        );
        if (result != EmailVerificationResult.SUCCESS) {
            throw new IllegalArgumentException(INVALID_VERIFICATION_MESSAGE);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        tokenRedisService.deleteRefreshToken(String.valueOf(user.getUserId()));
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }
        return email.trim();
    }

    private void validatePasswords(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("비밀번호는 최소 4자 이상이어야 합니다.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }
    }
}
