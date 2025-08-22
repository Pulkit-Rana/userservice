package com.syncnest.userservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Lightweight base exception carrying HTTP semantics for RFC 7807 responses.
 * Throw these from services/controllers; GlobalExceptionHandler maps them.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;   // e.g., https://syncnest.dev/problems/not-found
    private final String title;  // short summary for ProblemDetail title

    protected ApiException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }

    /** Optional machine-readable error code (override if needed). */
    public String code() { return null; }
}
