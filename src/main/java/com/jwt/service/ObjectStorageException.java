package com.jwt.service;

public class ObjectStorageException extends RuntimeException {
    private final boolean notFound;

    public ObjectStorageException(String message, Throwable cause, boolean notFound) {
        super(message, cause);
        this.notFound = notFound;
    }

    public boolean isNotFound() {
        return notFound;
    }
}
