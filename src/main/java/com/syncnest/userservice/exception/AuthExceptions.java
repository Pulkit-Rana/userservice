package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Authentication-specific exceptions for login, JWT, and security failures.
 *
 * All exceptions include:
 *  - Error code: Machine-readable code (AUTH_*)
 *  - Detailed reason: Specific security failure description
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 */
public final class AuthExceptions {

    private AuthExceptions() {}

    /** 401 Unauthorized – Invalid email or password. */
    public static final class InvalidCredentials extends ApiException {
        public InvalidCredentials(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/invalid-credentials",
                    "Invalid Credentials",
                    ErrorCode.AUTH_001,
                    detail != null ? detail : ErrorCode.AUTH_001.getMessage());
        }
    }

    /** 423 Locked – User account is suspended or disabled. */
    public static final class AccountSuspended extends ApiException {
        public AccountSuspended(String detail) {
            super(HttpStatus.LOCKED,
                    "https://syncnest.dev/problems/account-suspended",
                    "Account Suspended",
                    ErrorCode.AUTH_002,
                    detail != null ? detail : ErrorCode.AUTH_002.getMessage());
        }
    }

    /** 412 Precondition Failed – Account not verified. */
    public static final class AccountNotVerified extends ApiException {
        public AccountNotVerified(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/account-not-verified",
                    "Account Not Verified",
                    ErrorCode.AUTH_003,
                    detail != null ? detail : ErrorCode.AUTH_003.getMessage());
        }
    }

    /** 401 Unauthorized – JWT token has expired. */
    public static final class JwtExpired extends ApiException {
        public JwtExpired(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/jwt-expired",
                    "JWT Expired",
                    ErrorCode.AUTH_005,
                    detail != null ? detail : ErrorCode.AUTH_005.getMessage());
        }
    }

    /** 401 Unauthorized – JWT token is invalid or malformed. */
    public static final class JwtInvalid extends ApiException {
        public JwtInvalid(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/jwt-invalid",
                    "JWT Invalid",
                    ErrorCode.AUTH_006,
                    detail != null ? detail : ErrorCode.AUTH_006.getMessage());
        }
    }

    /** 401 Unauthorized – JWT token has been blacklisted. */
    public static final class JwtBlacklisted extends ApiException {
        public JwtBlacklisted(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/jwt-blacklisted",
                    "JWT Blacklisted",
                    ErrorCode.AUTH_007,
                    detail != null ? detail : ErrorCode.AUTH_007.getMessage());
        }
    }

    /** 400 Bad Request – Authorization header is missing. */
    public static final class MissingAuthHeader extends ApiException {
        public MissingAuthHeader(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/missing-auth-header",
                    "Missing Authorization Header",
                    ErrorCode.AUTH_008,
                    detail != null ? detail : ErrorCode.AUTH_008.getMessage());
        }
    }

    /** 400 Bad Request – Bearer token is missing or malformed. */
    public static final class MalformedBearerToken extends ApiException {
        public MalformedBearerToken(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/malformed-bearer-token",
                    "Malformed Bearer Token",
                    ErrorCode.AUTH_009,
                    detail != null ? detail : ErrorCode.AUTH_009.getMessage());
        }
    }

    /** 404 Not Found – User no longer exists. */
    public static final class UserNoLongerExists extends ApiException {
        public UserNoLongerExists(String detail) {
            super(HttpStatus.NOT_FOUND,
                    "https://syncnest.dev/problems/user-no-longer-exists",
                    "User No Longer Exists",
                    ErrorCode.AUTH_010,
                    detail != null ? detail : ErrorCode.AUTH_010.getMessage());
        }
    }

    /** 401 Unauthorized – Generic authentication failed. */
    public static final class AuthenticationFailed extends ApiException {
        public AuthenticationFailed(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/authentication-failed",
                    "Authentication Failed",
                    ErrorCode.AUTH_001,
                    detail != null ? detail : "Authentication failed. Please verify credentials");
        }
    }
}

