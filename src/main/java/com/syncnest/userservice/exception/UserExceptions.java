package com.syncnest.userservice.exception;

import com.syncnest.userservice.logging.LogSanitizer;
import org.springframework.http.HttpStatus;

/**
 * User-domain specific exceptions (registration, activation, profile lifecycle).
 * Keep authentication/authorization failures in security layer unless strictly user-state related.
 *
 * All exceptions now include:
 *  - Error code: Machine-readable code (USER_001, USER_002, etc.)
 *  - Detailed reason: Descriptive failure message for API response
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 */
public final class UserExceptions {

    private UserExceptions() {}

    /** 404 Not Found – User record not present. */
    public static final class UserNotFound extends ApiException {
        public UserNotFound(String detail) {
            super(HttpStatus.NOT_FOUND,
                    "https://syncnest.dev/problems/user-not-found",
                    "User Not Found",
                    ErrorCode.USER_001,
                    detail != null ? detail : ErrorCode.USER_001.getMessage());
        }
    }

    /** 409 Conflict – Email/username already in use or uniqueness violated. */
    public static final class UserAlreadyExists extends ApiException {
        public UserAlreadyExists(String email) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/user-already-exists",
                    "User Already Exists",
                    ErrorCode.USER_002,
                    "An account with email '" + LogSanitizer.maskEmail(email) + "' already exists.");
        }
    }

    /** 423 Locked – User is inactive/locked/disabled; action not allowed. */
    public static final class UserInactive extends ApiException {
        public UserInactive(String detail) {
            super(HttpStatus.LOCKED,
                    "https://syncnest.dev/problems/user-inactive",
                    "User Inactive",
                    ErrorCode.USER_003,
                    detail != null ? detail : ErrorCode.USER_003.getMessage());
        }
    }

    /**
     * 400 Bad Request – Provided credentials/profile attributes invalid for operation
     * (e.g., weak password, invalid profile updates).
     */
    public static final class InvalidUserInput extends ApiException {
        public InvalidUserInput(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-user-input",
                    "Invalid User Input",
                    ErrorCode.USER_007,
                    detail != null ? detail : ErrorCode.USER_007.getMessage());
        }
    }

    /**
     * 412 Precondition Failed – Required user preconditions unmet
     * (e.g., email not verified but operation requires verified email).
     */
    public static final class UserPreconditionFailed extends ApiException {
        public UserPreconditionFailed(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/user-precondition-failed",
                    "User Precondition Failed",
                    ErrorCode.USER_005,
                    detail != null ? detail : ErrorCode.USER_005.getMessage());
        }
    }

    /**
     * 409 Conflict – Attempt to change immutable identity fields or violates domain constraints.
     */
    public static final class UserUpdateConflict extends ApiException {
        public UserUpdateConflict(String detail) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/user-update-conflict",
                    "User Update Conflict",
                    ErrorCode.USER_006,
                    detail != null ? detail : ErrorCode.USER_006.getMessage());
        }
    }

    /** 423 Locked – User account is locked. */
    public static final class UserLocked extends ApiException {
        public UserLocked(String detail) {
            super(HttpStatus.LOCKED,
                    "https://syncnest.dev/problems/user-locked",
                    "User Locked",
                    ErrorCode.USER_004,
                    detail != null ? detail : ErrorCode.USER_004.getMessage());
        }
    }

    /** 412 Precondition Failed – Email not verified. */
    public static final class EmailNotVerified extends ApiException {
        public EmailNotVerified(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/email-not-verified",
                    "Email Not Verified",
                    ErrorCode.USER_008,
                    detail != null ? detail : ErrorCode.USER_008.getMessage());
        }
    }

}
