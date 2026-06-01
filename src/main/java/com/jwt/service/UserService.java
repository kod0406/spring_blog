package com.jwt.service;

import com.jwt.dto.RegistrationDto;
import com.jwt.emum.EmailVerificationResult;
import com.jwt.entity.User;
import com.jwt.entity.UserRole;
import com.jwt.entity.UserStatus;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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

    @Value("${app.email-verification.enabled:false}")
    private boolean emailVerificationEnabled;

    @Value("${app.owner.email:}")
    private String ownerEmail;

    @Value("${app.owner.password:}")
    private String ownerPassword;

    @Value("${app.owner.name:Owner}")
    private String ownerName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.OWNER)) {
            return;
        }
        if (ownerEmail == null || ownerEmail.isBlank() || ownerPassword == null || ownerPassword.isBlank()) {
            log.warn("OWNER_EMAIL/OWNER_PASSWORD not set. Owner account bootstrap skipped.");
            return;
        }

        User owner = User.builder()
                .name(ownerName)
                .email(ownerEmail.trim())
                .password(passwordEncoder.encode(ownerPassword))
                .role(UserRole.OWNER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(owner);
        log.info("Owner account bootstrapped. email={}", owner.getEmail());
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
    public List<User> getPendingUsers() {
        return userRepository.findByStatusOrderByUserIdAsc(UserStatus.PENDING);
    }

    @Transactional
    public User approveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setStatusEnum(UserStatus.ACTIVE);
        return user;
    }

    @Transactional
    public User rejectUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setStatusEnum(UserStatus.REJECTED);
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
