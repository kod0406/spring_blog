package com.jwt.exception;

import org.springframework.http.HttpStatus;

public class RangeNotSatisfiableException extends AppException {
    public RangeNotSatisfiableException(String message) {
        super(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, message);
    }
}
