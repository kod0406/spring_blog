package com.jwt.service;

import com.jwt.emum.EmailVerificationResult;
import com.jwt.emum.EmailVerificationPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String VERIFICATION_CODE_PREFIX = "email_verification:";
    private static final long VERIFICATION_CODE_EXPIRE_TIME = 5; // 5분
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, CachedVerificationCode> fallbackCodes = new ConcurrentHashMap<>();

    /**
     * 간단한 텍스트 메일 발송
     */
    public void sendSimpleMailMessage(String toEmail) {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();

        try {
            // 메일을 받을 수신자 설정
            simpleMailMessage.setTo(toEmail);
            // 메일의 제목 설정
            simpleMailMessage.setSubject("동아리 세션 - 테스트 메일");
            // 메일의 내용 설정
            simpleMailMessage.setText("동아리 세션 테스트 메일입니다.");

            javaMailSender.send(simpleMailMessage);

            log.info("메일 발송 성공! 수신자: {}", toEmail);
        } catch (Exception e) {
            log.error("메일 발송 실패! 수신자: {}, 오류: {}", toEmail, e.getMessage());
            throw new RuntimeException("메일 발송에 실패했습니다.", e);
        }
    }

    /**
     * HTML 형식의 메일 발송
     */
    public void sendMimeMessage(String toEmail) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        String verificationCode = generateVerificationCode();

        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            // 메일을 받을 수신자 설정
            mimeMessageHelper.setTo(toEmail);
            // 메일의 제목 설정
            mimeMessageHelper.setSubject("동아리 세션 - HTML 테스트 메일");

            // HTML 내용
            String content = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <div style="margin:50px; font-family: Arial, sans-serif;">
                        <h1 style="color: #4CAF50;">동아리 세션</h1>
                        <br>
                        <div style="border:2px solid #4CAF50; padding:20px; border-radius:10px;">
                            <h3>이메일 발송 테스트가 성공했습니다! ✅</h3>
                            <p>수신자: %s</p>
                            <p>발송 시간: %s</p>
                        </div>
                        <br/>
                        <p style="color: #666;">Spring Boot 프로젝트에서 발송된 메일입니다.</p>
                    </div>
                    </body>
                    </html>
                    """.formatted(toEmail, java.time.LocalDateTime.now());

            // 메일의 내용 설정 (HTML 형식)
            mimeMessageHelper.setText(content, true);

            javaMailSender.send(mimeMessage);

            log.info("HTML 메일 발송 성공! 수신자: {}", toEmail);
        } catch (Exception e) {
            log.error("HTML 메일 발송 실패! 수신자: {}, 오류: {}", toEmail, e.getMessage());
            throw new RuntimeException("HTML 메일 발송에 실패했습니다.", e);
        }
    }

    /**
     * 6자리 랜덤 인증번호 생성
     */
    public String generateVerificationCode() {
        int code = 100000 + SECURE_RANDOM.nextInt(900000); // 100000~999999
        return String.valueOf(code);
    }

    /**
     * 회원가입 인증 코드 이메일 발송
     */
    public void sendVerificationEmail(String toEmail) {
        sendVerificationEmail(toEmail, EmailVerificationPurpose.REGISTRATION);
    }

    public void sendPasswordResetVerificationEmail(String toEmail) {
        sendVerificationEmail(toEmail, EmailVerificationPurpose.PASSWORD_RESET);
    }

    private void sendVerificationEmail(String toEmail, EmailVerificationPurpose purpose) {
        String normalizedEmail = requireEmail(toEmail);
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        String verificationCode = generateVerificationCode();

        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            // 메일을 받을 수신자 설정
            mimeMessageHelper.setTo(normalizedEmail);
            // 메일의 제목 설정
            mimeMessageHelper.setSubject(purpose.getSubject());

            // HTML 내용
            String content = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <div style="margin:50px; font-family: Arial, sans-serif;">
                        <h1 style="color: #4CAF50;">%s</h1>
                        <br>
                        <div style="border:2px solid #4CAF50; padding:20px; border-radius:10px;">
                            <h3>인증 코드: <span style="color: #ff6b6b; font-size: 24px; font-weight: bold;">%s</span></h3>
                            <p>위의 인증 코드를 회원가입 페이지에 입력해주세요.</p>
                            <p><strong>인증 코드는 5분 후 만료됩니다.</strong></p>
                        </div>
                        <br/>
                        <p style="color: #666;">본 메일은 회원가입 인증을 위해 발송된 메일입니다.</p>
                    </div>
                    </body>
                    </html>
                    """.formatted(purpose.getSubject(), verificationCode);

            // 메일의 내용 설정 (HTML 형식)
            mimeMessageHelper.setText(content, true);

            javaMailSender.send(mimeMessage);

            // Redis에 인증 코드 저장
            String key = verificationKey(normalizedEmail, purpose);
            saveVerificationCode(key, verificationCode);

            log.info("인증 이메일 발송 성공. purpose={}, recipient={}", purpose, normalizedEmail);
        } catch (Exception e) {
            log.error("인증 이메일 발송 실패. purpose={}, recipient={}, error={}", purpose, normalizedEmail, e.getMessage());
            throw new RuntimeException("인증 이메일 발송에 실패했습니다.", e);
        }
    }
    /**
     * 이메일 인증 코드 검증 (상세 결과 반환)
     */
    public EmailVerificationResult verifyEmailCodeWithDetails(String email, String inputCode) {
        return verifyEmailCode(email, inputCode, EmailVerificationPurpose.REGISTRATION);
    }

    public EmailVerificationResult verifyPasswordResetCode(String email, String inputCode) {
        return verifyEmailCode(email, inputCode, EmailVerificationPurpose.PASSWORD_RESET);
    }

    private EmailVerificationResult verifyEmailCode(String email, String inputCode, EmailVerificationPurpose purpose) {
        String normalizedEmail = requireEmail(email);
        String key = verificationKey(normalizedEmail, purpose);
        String storedCode = getVerificationCode(key);

        if (storedCode == null) {
            log.warn("인증 코드가 만료되었거나 존재하지 않음. purpose={}, email={}", purpose, normalizedEmail);
            return EmailVerificationResult.EXPIRED;
        }

        boolean isValid = storedCode.equals(inputCode);

        if (isValid) {
            // 인증 성공 시 Redis에서 삭제
            deleteVerificationCode(key);
            log.info("이메일 인증 성공. purpose={}, email={}", purpose, normalizedEmail);
            return EmailVerificationResult.SUCCESS;
        } else {
            log.warn("이메일 인증 실패 - 잘못된 코드. purpose={}, email={}", purpose, normalizedEmail);
            return EmailVerificationResult.INVALID_CODE;
        }
    }

    private String verificationKey(String email, EmailVerificationPurpose purpose) {
        return VERIFICATION_CODE_PREFIX + purpose.getKey() + ":" + email;
    }

    private void saveVerificationCode(String key, String code) {
        try {
            redisTemplate.opsForValue().set(key, code, VERIFICATION_CODE_EXPIRE_TIME, TimeUnit.MINUTES);
            fallbackCodes.remove(key);
        } catch (RuntimeException e) {
            fallbackCodes.put(key, new CachedVerificationCode(code, Instant.now().plusSeconds(VERIFICATION_CODE_EXPIRE_TIME * 60)));
            log.warn("Redis unavailable. Verification code stored in memory for this app instance. key={}", key);
        }
    }

    private String getVerificationCode(String key) {
        try {
            String code = redisTemplate.opsForValue().get(key);
            if (code != null) {
                return code;
            }
        } catch (RuntimeException e) {
            log.warn("Redis unavailable while reading verification code. key={}", key);
        }

        CachedVerificationCode cachedCode = fallbackCodes.get(key);
        if (cachedCode == null || cachedCode.expiresAt().isBefore(Instant.now())) {
            fallbackCodes.remove(key);
            return null;
        }
        return cachedCode.code();
    }

    private void deleteVerificationCode(String key) {
        fallbackCodes.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable while deleting verification code. key={}", key);
        }
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }
        return email.trim();
    }

    private record CachedVerificationCode(String code, Instant expiresAt) {
    }
}
