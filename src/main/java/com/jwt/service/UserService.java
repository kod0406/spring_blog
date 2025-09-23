package com.jwt.service;

import com.jwt.dto.RegistrationDto;
import com.jwt.entity.User;
import com.jwt.emum.EmailVerificationResult;
import com.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService; // EmailService 의존성 추가
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder 빈이 주입됨 (세션때 빈칸 넣을곳)

    @Transactional
    public User registerUser(RegistrationDto requestDto) {
        if(userRepository.findByEmail(requestDto.getEmail()) != null){
            log.warn("[회원가입 실패] 이미 존재하는 이메일: {}", requestDto.getEmail());
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
        // 이메일 인증 코드 검증
        if (__________ == null || __________.trim().isEmpty()) { // 인증 코드 입력 여부 확인
            log.warn("[회원가입 실패] 인증 코드가 입력되지 않음: {}", __________);
            throw new IllegalArgumentException("__________");
        }

        EmailVerificationResult verificationResult = emailService.verifyEmailCodeWithDetails(requestDto.getEmail(), requestDto.getVerificationCode());

        if (verificationResult __________ EmailVerificationResult.SUCCESS) { // 인증 성공 여부 확인
            log.warn("[회원가입 실패] 이메일 인증 실패: {}, 결과: {}", __________, verificationResult);
            switch (verificationResult) {
                case EXPIRED:
                    throw new IllegalArgumentException("__________");
                case INVALID_CODE:
                    throw new IllegalArgumentException("__________");
                default:
            throw new IllegalArgumentException("__________");
    }
}

        // DTO에서 Entity로 변환
        User user = User.builder()
                .name(requestDto.getName())
                .email(requestDto.getEmail())
                .password(passwordEncoder.encode(requestDto.getPassword())) // (세션때 빈칸 넣을곳)
                .role("USER")
                .build();
        userRepository.save(user);
        log.info("[회원가입 성공] 사용자 ID: {}, 이메일: {}", user.getUserId(), user.getEmail());
        return user;
    }

    public User authenticateUser(String email, String rawPassword) {
        User user = userRepository.findByEmail(email);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("[로그인 실패] 이메일 또는 비밀번호 불일치: {}", email);
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }
        log.info("[로그인 성공] 사용자 ID: {}, 이메일: {}", user.getUserId(), user.getEmail());
        return user;
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            log.warn("[비밀번호 재설정 실패] 존재하지 않는 이메일: {}", email);
            throw new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다.");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("[비밀번호 재설정 성공] 사용자 ID: {}, 이메일: {}", user.getUserId(), user.getEmail());
    }

    // 이메일로 사용자 찾기
    public User findByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("해당 이메일로 가입된 계정이 없습니다.");
        }
        return user;
    }

    // 비밀번호 업데이트 (resetPassword와 동일한 기능이지만 명명 일관성을 위해 추가)
    @Transactional
    public void updatePassword(String email, String newPassword) {
        resetPassword(email, newPassword);
    }

}