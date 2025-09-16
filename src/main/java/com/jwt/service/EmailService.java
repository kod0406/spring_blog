package com.jwt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

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
    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 100000~999999
        return String.valueOf(code);
    }
}