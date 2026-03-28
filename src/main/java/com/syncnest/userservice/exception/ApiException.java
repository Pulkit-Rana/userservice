package com.syncnest.userservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Lightweight base exception carrying HTTP semantics for RFC 7807 responses.
 * Throw these from services/controllers; GlobalExceptionHandler maps them.
 *
 * Now includes:
 * - errorCode: Machine-readable error code (e.g., AUTH_001, OTP_005)
 * - detail: Detailed failure reason for clients
 * - type: RFC 7807 problem type URI
 * - title: Short human-readable summary
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;           // e.g., https://syncnest.dev/problems/not-found
    private final String title;          // short summary for ProblemDetail title
    private final ErrorCode errorCode;   // machine-readable error code
    private final String detailReason;   // detailed failure reason for API response

    protected ApiException(HttpStatus status, String type, String title, ErrorCode errorCode, String detailReason) {
        super(detailReason);
        this.status = status;
        this.type = type;
        this.title = title;
        this.errorCode = errorCode;
        this.detailReason = detailReason != null ? detailReason : errorCode.getMessage();
    }

    /**
     * Legacy constructor for backward compatibility
     */
    protected ApiException(HttpStatus status, String type, String title, String detail) {
        this(status, type, title, ErrorCode.SYSTEM_001, detail);
    }

    /**
     * Get the error code string
     */
    public String getErrorCode() {
        return errorCode != null ? errorCode.getCode() : "UNKNOWN";
    }

    /**
     * Get detailed failure reason
     */
    public String getDetailReason() {
        return detailReason;
    }
}
