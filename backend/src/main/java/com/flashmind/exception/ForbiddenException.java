package com.flashmind.exception;

/**
 * Thrown when a user is authenticated but does not own the resource being accessed.
 * Mapped to HTTP 403 in {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
