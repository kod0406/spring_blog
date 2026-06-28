package com.jwt.jwt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JwtTokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;
}
