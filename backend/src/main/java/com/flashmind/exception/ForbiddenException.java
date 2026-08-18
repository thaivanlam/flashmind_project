package com.flashmind.exception;

/**
 * Ném ra khi user đã đăng nhập nhưng không sở hữu tài nguyên đang truy cập.
 * Được map thành HTTP 403 trong {@link GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
