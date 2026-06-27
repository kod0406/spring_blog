package com.jwt.emum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailVerificationPurpose {
    REGISTRATION("registration", "회원가입 이메일 인증"),
    PASSWORD_RESET("password-reset", "비밀번호 재설정 인증");

    private final String key;
    private final String subject;
}
