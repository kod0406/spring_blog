package com.jwt.service;

import com.jwt.dto.RegistrationDto;
import com.jwt.emum.EmailVerificationResult;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.exception.NotFoundException;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements ApplicationRunner {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    @Value("${app.email-verification.enabled:false}")
    private boolean emailVerificationEnabled;

    @Value("${app.admin.email:${app.owner.email:}}")
    private String adminEmail;

    @Value("${app.admin.password:${app.owner.password:}}")
    private String adminPassword;

    @Value("${app.admin.name:${app.owner.name:Admin}}")
    private String adminName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        normalizeOwnerRole();
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return;
        }
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD not set. Admin account bootstrap skipped.");
            return;
        }

        User admin = User.builder()
                .name(adminName)
                .email(adminEmail.trim())
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);
        log.info("Admin account bootstrapped. email={}", admin.getEmail());
    }

    @Transactional
    public User registerUser(RegistrationDto requestDto) {
        validateRegistration(requestDto);
        String email = requestDto.getEmail().trim();

        if (userRepository.findByEmail(email) != null) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        verifyEmailIfEnabled(requestDto);

        User user = User.builder()
                .name(requestDto.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(requestDto.getPassword()))
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build();

        return userRepository.save(user);
    }

    public User authenticateUser(String email, String rawPassword) {
        User user = userRepository.findByEmail(email);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }
        if (user.getStatusEnum() == UserStatus.PENDING) {
            throw new IllegalArgumentException("관리자 승인 대기 중입니다.");
        }
        if (user.getStatusEnum() == UserStatus.REJECTED) {
            throw new IllegalArgumentException("가입 승인이 거절된 계정입니다.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<User> getPendingUsers(User admin) {
        authorizationService.requireAdmin(admin);
        return userRepository.findByStatusOrderByUserIdAsc(UserStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers(User admin) {
        authorizationService.requireAdmin(admin);
        return userRepository.findAllByOrderByUserIdAsc();
    }

    @Transactional
    public User approveUser(Long userId, User admin) {
        authorizationService.requireAdmin(admin);
        User user = getUser(userId);
        user.setStatusEnum(UserStatus.ACTIVE);
        return user;
    }

    @Transactional
    public User rejectUser(Long userId, User admin) {
        authorizationService.requireAdmin(admin);
        User user = getUser(userId);
        user.setStatusEnum(UserStatus.REJECTED);
        return user;
    }

    @Transactional
    public User updateAdminFields(Long userId, String role, String status, User admin) {
        authorizationService.requireAdmin(admin);
        User user = getUser(userId);
        if (role != null && !role.isBlank()) {
            user.setRole(role);
        }
        if (status != null && !status.isBlank()) {
            user.setStatus(status);
        }
        return user;
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("비밀번호는 최소 4자 이상이어야 합니다.");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
    }

    public User findByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다.");
        }
        return user;
    }

    @Transactional
    public void updatePassword(String email, String newPassword) {
        resetPassword(email, newPassword);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }

    private void normalizeOwnerRole() {
        try {
            int updated = jdbcTemplate.update("update users set role = 'ADMIN' where role = 'OWNER'");
            if (updated > 0) {
                log.info("Normalized legacy OWNER users to ADMIN. count={}", updated);
            }
        } catch (RuntimeException e) {
            log.debug("Legacy OWNER role normalization skipped. reason={}", e.getMessage());
        }
    }

    private void validateRegistration(RegistrationDto requestDto) {
        if (requestDto == null) {
            throw new IllegalArgumentException("회원가입 정보가 없습니다.");
        }
        if (requestDto.getName() == null || requestDto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("이름을 입력해 주세요.");
        }
        if (requestDto.getEmail() == null || requestDto.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }
        if (requestDto.getPassword() == null || requestDto.getPassword().length() < 4) {
            throw new IllegalArgumentException("비밀번호는 최소 4자 이상이어야 합니다.");
        }
    }

    private void verifyEmailIfEnabled(RegistrationDto requestDto) {
        if (!emailVerificationEnabled) {
            return;
        }

        if (requestDto.getVerificationCode() == null || requestDto.getVerificationCode().trim().isEmpty()) {
            throw new IllegalArgumentException("이메일 인증 코드를 입력해 주세요.");
        }

        EmailVerificationResult verificationResult = emailService.verifyEmailCodeWithDetails(
                requestDto.getEmail(),
                requestDto.getVerificationCode()
        );

        if (verificationResult == EmailVerificationResult.SUCCESS) {
            return;
        }
        if (verificationResult == EmailVerificationResult.EXPIRED) {
            throw new IllegalArgumentException("인증 코드가 만료되었습니다. 새 인증 코드를 요청해 주세요.");
        }
        if (verificationResult == EmailVerificationResult.INVALID_CODE) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다.");
        }
        throw new IllegalArgumentException("이메일 인증에 실패했습니다.");
    }
}
