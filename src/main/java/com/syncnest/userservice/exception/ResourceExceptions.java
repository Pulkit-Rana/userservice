package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceptions representing resource lifecycle/errors (not found, state conflicts,
 * optimistic locking/version mismatches, etc.).
 *
 * All exceptions include:
 *  - Error code: Machine-readable code (SYSTEM_004, SYSTEM_010, etc.)
 *  - Detailed reason: Specific description of the resource issue
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 */
public final class ResourceExceptions {

    private ResourceExceptions() {}

    /** 404 Not Found – Target resource does not exist. */
    public static final class NotFound extends ApiException {
        public NotFound(String detail) {
            super(HttpStatus.NOT_FOUND,
                    "https://syncnest.dev/problems/not-found",
                    "Resource Not Found",
                    ErrorCode.SYSTEM_004,
                    detail != null ? detail : ErrorCode.SYSTEM_004.getMessage());
        }
    }

    /** 409 Conflict – State conflict (e.g., duplicate unique key, illegal transition). */
    public static final class Conflict extends ApiException {
        public Conflict(String detail) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/conflict",
                    "Conflict",
                    ErrorCode.SYSTEM_010,
                    detail != null ? detail : ErrorCode.SYSTEM_010.getMessage());
        }
    }

    /** 410 Gone – Resource existed before but has been permanently removed. */
    public static final class Gone extends ApiException {
        public Gone(String detail) {
            super(HttpStatus.GONE,
                    "https://syncnest.dev/problems/gone",
                    "Resource Gone",
                    ErrorCode.SYSTEM_004,
                    detail != null ? detail : "The requested resource is no longer available");
        }
    }

    /** 423 Locked – Resource is locked/disabled and cannot be modified. */
    public static final class Locked extends ApiException {
        public Locked(String detail) {
            super(HttpStatus.LOCKED,
                    "https://syncnest.dev/problems/locked",
                    "Resource Locked",
                    ErrorCode.SYSTEM_001,
                    detail != null ? detail : "The requested resource is locked and cannot be modified");
        }
    }

    /** 409 Conflict – Version/ETag mismatch for optimistic concurrency control. */
    public static final class VersionConflict extends ApiException {
        public VersionConflict(String detail) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/version-conflict",
                    "Version Conflict",
                    ErrorCode.SYSTEM_010,
                    detail != null ? detail : "Resource version conflict. The resource may have been modified");
        }
    }

    /** 412 Precondition Failed – Missing required resource preconditions. */
    public static final class PreconditionRequired extends ApiException {
        public PreconditionRequired(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/precondition-required",
                    "Precondition Required",
                    ErrorCode.VALIDATION_010,
                    detail != null ? detail : "Required preconditions are not met for this resource");
        }
    }

    /** 409 Conflict – Duplicate resource already exists. */
    public static final class DuplicateResource extends ApiException {
        public DuplicateResource(String resourceType, String identifier) {
            super(HttpStatus.CONFLICT,
                    "https://syncnest.dev/problems/duplicate-resource",
                    "Duplicate Resource",
                    ErrorCode.SYSTEM_010,
                    resourceType + " with identifier '" + identifier + "' already exists");
        }
    }
}
