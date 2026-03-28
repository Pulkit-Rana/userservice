package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * OTP (One-Time Password) specific exceptions for generation, delivery, and verification.
 *
 * All exceptions include:
 *  - Error code: Machine-readable code (OTP_*)
 *  - Detailed reason: Specific OTP failure description
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 */
public final class OtpExceptions {

    private OtpExceptions() {}

    /** 400 Bad Request – OTP has expired and is no longer valid. */
    public static final class OtpExpired extends ApiException {
        public OtpExpired(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/otp-expired",
                    "OTP Expired",
                    ErrorCode.OTP_001,
                    detail != null ? detail : ErrorCode.OTP_001.getMessage());
        }
    }

    /** 400 Bad Request – OTP is invalid or not found. */
    public static final class OtpNotFound extends ApiException {
        public OtpNotFound(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/otp-not-found",
                    "OTP Not Found",
                    ErrorCode.OTP_008,
                    detail != null ? detail : ErrorCode.OTP_008.getMessage());
        }
    }

    /** 400 Bad Request – OTP provided is incorrect. */
    public static final class OtpIncorrect extends ApiException {
        public OtpIncorrect(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/otp-incorrect",
                    "OTP Incorrect",
                    ErrorCode.OTP_003,
                    detail != null ? detail : ErrorCode.OTP_003.getMessage());
        }
    }

    /** 429 Too Many Requests – Maximum OTP verification attempts exceeded. */
    public static final class MaxOtpAttemptsExceeded extends ApiException {
        public MaxOtpAttemptsExceeded(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/max-otp-attempts",
                    "Max OTP Attempts Exceeded",
                    ErrorCode.OTP_004,
                    detail != null ? detail : ErrorCode.OTP_004.getMessage());
        }
    }

    /** 429 Too Many Requests – Cooldown period active after max attempts. */
    public static final class OtpCooldownActive extends ApiException {
        public OtpCooldownActive(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/otp-cooldown",
                    "OTP Cooldown Active",
                    ErrorCode.OTP_005,
                    detail != null ? detail : ErrorCode.OTP_005.getMessage());
        }
    }

    /** 429 Too Many Requests – Must wait before requesting new OTP. */
    public static final class OtpResendRateLimited extends ApiException {
        public OtpResendRateLimited(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/otp-resend-rate-limited",
                    "OTP Resend Rate Limited",
                    ErrorCode.OTP_006,
                    detail != null ? detail : ErrorCode.OTP_006.getMessage());
        }
    }

    /** 429 Too Many Requests – OTP request quota exceeded. */
    public static final class OtpQuotaExceeded extends ApiException {
        public OtpQuotaExceeded(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/otp-quota-exceeded",
                    "OTP Quota Exceeded",
                    ErrorCode.OTP_007,
                    detail != null ? detail : ErrorCode.OTP_007.getMessage());
        }
    }

    /** 500 Internal Server Error – OTP generation failed. */
    public static final class OtpGenerationFailed extends ApiException {
        public OtpGenerationFailed(String detail) {
            super(HttpStatus.INTERNAL_SERVER_ERROR,
                    "https://syncnest.dev/problems/otp-generation-failed",
                    "OTP Generation Failed",
                    ErrorCode.OTP_009,
                    detail != null ? detail : ErrorCode.OTP_009.getMessage());
        }
    }

    /** 503 Service Unavailable – OTP email delivery failed. */
    public static final class OtpDeliveryFailed extends ApiException {
        public OtpDeliveryFailed(String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE,
                    "https://syncnest.dev/problems/otp-delivery-failed",
                    "OTP Delivery Failed",
                    ErrorCode.OTP_010,
                    detail != null ? detail : ErrorCode.OTP_010.getMessage());
        }
    }

    /** 400 Bad Request – Email not provided for OTP. */
    public static final class EmailRequiredForOtp extends ApiException {
        public EmailRequiredForOtp(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/email-required-for-otp",
                    "Email Required for OTP",
                    ErrorCode.VALIDATION_003,
                    detail != null ? detail : "Email is required to generate OTP");
        }
    }

    /** 400 Bad Request – OTP code format is invalid. */
    public static final class OtpInvalidFormat extends ApiException {
        public OtpInvalidFormat(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/otp-invalid-format",
                    "OTP Invalid Format",
                    ErrorCode.OTP_002,
                    detail != null ? detail : "OTP must be a 6-digit code");
        }
    }
}

