package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Refresh Token and Session management specific exceptions.
 *
 * All exceptions include:
 *  - Error code: Machine-readable code (TOKEN_*)
 *  - Detailed reason: Specific token/session failure description
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 */
public final class TokenExceptions {

    private TokenExceptions() {}

    /** 401 Unauthorized – Refresh token is invalid or expired. */
    public static final class RefreshTokenInvalid extends ApiException {
        public RefreshTokenInvalid(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/refresh-token-invalid",
                    "Refresh Token Invalid",
                    ErrorCode.TOKEN_001,
                    detail != null ? detail : ErrorCode.TOKEN_001.getMessage());
        }
    }

    /** 401 Unauthorized – Refresh token has expired. */
    public static final class RefreshTokenExpired extends ApiException {
        public RefreshTokenExpired(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/refresh-token-expired",
                    "Refresh Token Expired",
                    ErrorCode.TOKEN_002,
                    detail != null ? detail : ErrorCode.TOKEN_002.getMessage());
        }
    }

    /** 401 Unauthorized – Refresh token has been revoked. */
    public static final class RefreshTokenRevoked extends ApiException {
        public RefreshTokenRevoked(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/refresh-token-revoked",
                    "Refresh Token Revoked",
                    ErrorCode.TOKEN_003,
                    detail != null ? detail : ErrorCode.TOKEN_003.getMessage());
        }
    }

    /** 401 Unauthorized – Device mismatch for refresh token. */
    public static final class DeviceMismatch extends ApiException {
        public DeviceMismatch(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/device-mismatch",
                    "Device Mismatch",
                    ErrorCode.TOKEN_004,
                    detail != null ? detail : ErrorCode.TOKEN_004.getMessage());
        }
    }

    /** 404 Not Found – No active sessions found. */
    public static final class NoActiveSessions extends ApiException {
        public NoActiveSessions(String detail) {
            super(HttpStatus.NOT_FOUND,
                    "https://syncnest.dev/problems/no-active-sessions",
                    "No Active Sessions",
                    ErrorCode.TOKEN_005,
                    detail != null ? detail : ErrorCode.TOKEN_005.getMessage());
        }
    }

    /** 401 Unauthorized – Session has expired. */
    public static final class SessionExpired extends ApiException {
        public SessionExpired(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/session-expired",
                    "Session Expired",
                    ErrorCode.TOKEN_006,
                    detail != null ? detail : ErrorCode.TOKEN_006.getMessage());
        }
    }

    /** 429 Too Many Requests – Maximum concurrent sessions limit reached. */
    public static final class MaxSessionsExceeded extends ApiException {
        public MaxSessionsExceeded(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/max-sessions-exceeded",
                    "Max Sessions Exceeded",
                    ErrorCode.TOKEN_007,
                    detail != null ? detail : ErrorCode.TOKEN_007.getMessage());
        }
    }

    /** 500 Internal Server Error – Token hashing failed. */
    public static final class TokenHashingFailed extends ApiException {
        public TokenHashingFailed(String detail) {
            super(HttpStatus.INTERNAL_SERVER_ERROR,
                    "https://syncnest.dev/problems/token-hashing-failed",
                    "Token Hashing Failed",
                    ErrorCode.TOKEN_008,
                    detail != null ? detail : ErrorCode.TOKEN_008.getMessage());
        }
    }

    /** 401 Unauthorized – Session is invalid. */
    public static final class SessionInvalid extends ApiException {
        public SessionInvalid(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/session-invalid",
                    "Session Invalid",
                    ErrorCode.TOKEN_009,
                    detail != null ? detail : ErrorCode.TOKEN_009.getMessage());
        }
    }

    /** 400 Bad Request – Refresh token is required. */
    public static final class RefreshTokenRequired extends ApiException {
        public RefreshTokenRequired(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/refresh-token-required",
                    "Refresh Token Required",
                    ErrorCode.TOKEN_010,
                    detail != null ? detail : ErrorCode.TOKEN_010.getMessage());
        }
    }

    /** 400 Bad Request – Device ID is required for this operation. */
    public static final class DeviceIdRequired extends ApiException {
        public DeviceIdRequired(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/device-id-required",
                    "Device ID Required",
                    ErrorCode.VALIDATION_003,
                    detail != null ? detail : "Device ID is required for this operation");
        }
    }

    /** 401 Unauthorized – Token binding mismatch. */
    public static final class TokenBindingMismatch extends ApiException {
        public TokenBindingMismatch(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/token-binding-mismatch",
                    "Token Binding Mismatch",
                    ErrorCode.TOKEN_004,
                    detail != null ? detail : "Token is bound to different session/device");
        }
    }

    /** 401 Unauthorized – Replay attack detected: a revoked refresh token was reused. */
    public static final class ReplayAttackDetected extends ApiException {
        public ReplayAttackDetected(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/replay-attack-detected",
                    "Replay Attack Detected",
                    ErrorCode.TOKEN_011,
                    detail != null ? detail : ErrorCode.TOKEN_011.getMessage());
        }
    }
}

