package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * User-domain specific exceptions (registration, activation, profile lifecycle).
 * Keep authentication/authorization failures in security layer unless strictly user-state related.
 *
 * Conventions:
 *  - type:  https://syncnest.dev/problems/<slug>
 */
public final class UserExceptions {

    private UserExceptions() {}

    /** 404 Not Found – User record not present. */
    public static final class UserNotFound extends ApiException {
        public UserNotFound(String detail) {
            super(HttpStatus.NOT_FOUND,
                    "https://syncnest.dev/problems/user-not-found",
                    "User Not Found",
                    detail);
        }
    }

    /** 409 Conflict – Email/username already in use or uniqueness violated. */
    public static final class UserAlreadyExists extends ApiException {
        public UserAlreadyExists(String email) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/user-already-exists",
                    "User with email '" + email + "' already exists.",
                    email);
        }
    }

    /** 423 Locked – User is inactive/locked/disabled; action not allowed. */
    public static final class UserInactive extends ApiException {
        public UserInactive(String detail) {
            super(HttpStatus.LOCKED,
                    "https://syncnest.dev/problems/user-inactive",
                    "User Inactive",
                    detail);
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
                    detail);
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
                    detail);
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
                    detail);
        }
    }
}
