package com.jwt.emum;

public enum EmailVerificationResult {
    SUCCESS,        // 인증 성공
    INVALID_CODE,   // 인증 코드 불일치
    EXPIRED         // 인증 코드 만료
}
