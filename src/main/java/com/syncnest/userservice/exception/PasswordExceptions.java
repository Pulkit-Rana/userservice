package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Password reset flow exceptions (forgot/reset password).
 */
public final class PasswordExceptions {

    private PasswordExceptions() {}

    public static final class InvalidResetRequest extends ApiException {
        public InvalidResetRequest(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-reset-request",
                    "Invalid Reset Request",
                    ErrorCode.VALIDATION_003,
                    detail != null ? detail : "Invalid password reset request");
        }
    }

    public static final class ResetCodeInvalid extends ApiException {
        public ResetCodeInvalid(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/reset-code-invalid",
                    "Reset Code Invalid",
                    ErrorCode.PWD_001,
                    detail != null ? detail : ErrorCode.PWD_001.getMessage());
        }
    }

    public static final class ResetCodeExpired extends ApiException {
        public ResetCodeExpired(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/reset-code-expired",
                    "Reset Code Expired",
                    ErrorCode.PWD_002,
                    detail != null ? detail : ErrorCode.PWD_002.getMessage());
        }
    }

    public static final class TooManyResetAttempts extends ApiException {
        public TooManyResetAttempts(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/reset-attempts-exceeded",
                    "Too Many Reset Attempts",
                    ErrorCode.PWD_003,
                    detail != null ? detail : ErrorCode.PWD_003.getMessage());
        }
    }

    public static final class ResetRequestRateLimited extends ApiException {
        public ResetRequestRateLimited(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/reset-request-rate-limited",
                    "Reset Request Rate Limited",
                    ErrorCode.PWD_004,
                    detail != null ? detail : ErrorCode.PWD_004.getMessage());
        }
    }

    public static final class PasswordResetFailed extends ApiException {
        public PasswordResetFailed(String detail) {
            super(HttpStatus.INTERNAL_SERVER_ERROR,
                    "https://syncnest.dev/problems/password-reset-failed",
                    "Password Reset Failed",
                    ErrorCode.PWD_005,
                    detail != null ? detail : ErrorCode.PWD_005.getMessage());
        }
    }
}

