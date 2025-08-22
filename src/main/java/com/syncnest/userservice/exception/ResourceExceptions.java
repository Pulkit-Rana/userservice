package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceptions representing resource lifecycle/errors (not found, state conflicts,
 * optimistic locking/version mismatches, etc.).
 *
 * Conventions:
 *  - type:  https://syncnest.dev/problems/<slug>
 */
public final class ResourceExceptions {

    private ResourceExceptions() {}

    /** 404 Not Found – Target resource does not exist. */
    public static final class NotFound extends ApiException {
        public NotFound(String detail) {
            super(HttpStatus.NOT_FOUND,
                    "https://syncnest.dev/problems/not-found",
                    "Resource Not Found",
                    detail);
        }
    }

    /** 409 Conflict – State conflict (e.g., duplicate unique key, illegal transition). */
    public static final class Conflict extends ApiException {
        public Conflict(String detail) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/conflict",
                    "Conflict",
                    detail);
        }
    }

    /** 410 Gone – Resource existed before but has been permanently removed. */
    public static final class Gone extends ApiException {
        public Gone(String detail) {
            super(HttpStatus.GONE,
                    "https://syncnest.dev/problems/gone",
                    "Resource Gone",
                    detail);
        }
    }

    /** 423 Locked – Resource is locked/disabled and cannot be modified. */
    public static final class Locked extends ApiException {
        public Locked(String detail) {
            super(HttpStatus.LOCKED,
                    "https://syncnest.dev/problems/locked",
                    "Resource Locked",
                    detail);
        }
    }

    /** 409 Conflict – Version/ETag mismatch for optimistic concurrency control. */
    public static final class VersionConflict extends ApiException {
        public VersionConflict(String detail) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/version-conflict",
                    "Version Conflict",
                    detail);
        }
    }

    /** 412 Precondition Failed – Missing required resource preconditions. */
    public static final class PreconditionRequired extends ApiException {
        public PreconditionRequired(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/precondition-required",
                    "Precondition Required",
                    detail);
        }
    }
}
