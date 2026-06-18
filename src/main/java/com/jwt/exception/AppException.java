package com.jwt.exception;

import org.springframework.http.HttpStatus;

public abstract class AppException extends IllegalArgumentException {
    private final HttpStatus status;

    protected AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
