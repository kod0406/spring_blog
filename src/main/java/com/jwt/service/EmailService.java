package com.jwt.service;

import com.jwt.emum.EmailVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String VERIFICATION_CODE_PREFIX = "email_verification:";
    private static final long VERIFICATION_CODE_EXPIRE_TIME = 5; // 5분

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
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 100000~999999
        return String.valueOf(code);
    }

    /**
     * 회원가입 인증 코드 이메일 발송
     */
    public String sendVerificationEmail(String toEmail) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        String verificationCode = generateVerificationCode();

        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            // 메일을 받을 수신자 설정
            mimeMessageHelper.setTo(toEmail);
            // 메일의 제목 설정
            mimeMessageHelper.setSubject("회원가입 이메일 인증");

            // HTML 내용
            String content = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <div style="margin:50px; font-family: Arial, sans-serif;">
                        <h1 style="color: #4CAF50;">회원가입 이메일 인증</h1>
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
                    """.formatted(verificationCode);

            // 메일의 내용 설정 (HTML 형식)
            mimeMessageHelper.setText(content, true);

            javaMailSender.send(mimeMessage);

            // Redis에 인증 코드 저장
            String key = VERIFICATION_CODE_PREFIX + toEmail;
            redisTemplate.opsForValue().set(key, verificationCode, VERIFICATION_CODE_EXPIRE_TIME, TimeUnit.MINUTES);

            log.info("인증 이메일 발송 성공! 수신자: {}", toEmail);
            return verificationCode;
        } catch (Exception e) {
            log.error("인증 이메일 발송 실패! 수신자: {}, 오류: {}", toEmail, e.getMessage());
            throw new RuntimeException("인증 이메일 발송에 실패했습니다.", e);
        }
    }

    /**
     * 이메일 인증 코드 검증 (boolean 함수)
     */
    public boolean verifyEmailCode(String email, String inputCode) {
        String key = VERIFICATION_CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            log.warn("인증 코드가 만료되었거나 존재하지 않음: {}", email);
            return false;
        }

        boolean isValid = storedCode.equals(inputCode);

        if (isValid) {
            // 인증 성공 시 Redis에서 삭제
            redisTemplate.delete(key);
            log.info("이메일 인증 성공: {}", email);
        } else {
            log.warn("이메일 인증 실패 - 잘못된 코드: {}", email);
        }

        return isValid;
    }

    /**
     * 이메일 인증 코드 검증 (상세 결과 반환)
     */
    public EmailVerificationResult verifyEmailCodeWithDetails(String email, String inputCode) {
        String key = VERIFICATION_CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            log.warn("인증 코드가 만료되었거나 존재하지 않음: {}", email);
            return EmailVerificationResult.EXPIRED;
        }

        boolean isValid = storedCode.equals(inputCode);

        if (isValid) {
            // 인증 성공 시 Redis에서 삭제
            redisTemplate.delete(key);
            log.info("이메일 인증 성공: {}", email);
            return EmailVerificationResult.SUCCESS;
        } else {
            log.warn("이메일 인증 실패 - 잘못된 코드: {}", email);
            return EmailVerificationResult.INVALID_CODE;
        }
    }
