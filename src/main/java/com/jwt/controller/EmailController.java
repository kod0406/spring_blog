package com.jwt.controller;

import com.jwt.dto.EmailDto;
import com.jwt.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailService mailService;

    /**
     * 간단한 텍스트 메일 발송
     */
    @PostMapping("/email/send-simple")
    public ResponseEntity<Map<String, String>> sendSimpleEmail(@RequestBody EmailDto emailDto) {
        try {
            mailService.sendSimpleMailMessage(emailDto.getEmail());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "간단한 메일이 성공적으로 발송되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("간단한 메일 발송 실패: {}", e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "메일 발송에 실패했습니다: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * HTML 메일 발송
     */
    @PostMapping("/email/send-html")
    public ResponseEntity<Map<String, String>> sendHtmlEmail(@RequestBody EmailDto emailDto) {
        try {
            mailService.sendMimeMessage(emailDto.getEmail());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "HTML 메일이 성공적으로 발송되었습니다.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("HTML 메일 발송 실패: {}", e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "메일 발송에 실패했습니다: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 회원가입 인증 코드 이메일 발송
     */
    @PostMapping("/email/send-verification")
    public ResponseEntity<Map<String, String>> sendVerificationEmail(@RequestBody EmailDto emailDto) {
        try {
            mailService.sendVerificationEmail(emailDto.getEmail());

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "인증 코드가 이메일로 발송되었습니다. (5분 후 만료)");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("인증 코드 발송 실패: {}", e.getMessage());

            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "인증 코드 발송에 실패했습니다: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
