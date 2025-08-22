package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceptions representing problems with the incoming client request itself
 * (malformed input, invalid state, headers/media, etc.).
 *
 * Conventions:
 *  - type:  https://syncnest.dev/problems/<slug>
 *  - title: short, human-readable summary
 *  - detail: safe, non-sensitive explanation suitable for clients
 */
public final class RequestExceptions {

    private RequestExceptions() {}

    /** 400 Bad Request – Request is syntactically incorrect or semantically invalid (generic). */
    public static final class BadRequest extends ApiException {
        public BadRequest(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/bad-request",
                    "Bad Request",
                    detail);
        }
    }

    /** 400 Bad Request – A required parameter is missing/blank/invalid. */
    public static final class InvalidParameter extends ApiException {
        public InvalidParameter(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-parameter",
                    "Invalid Parameter",
                    detail);
        }
    }

    /** 400 Bad Request – Required header is missing or invalid. */
    public static final class InvalidHeader extends ApiException {
        public InvalidHeader(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-header",
                    "Invalid Header",
                    detail);
        }
    }

    /** 413 Payload Too Large – Body exceeds configured limits. */
    public static final class PayloadTooLarge extends ApiException {
        public PayloadTooLarge(String detail) {
            super(HttpStatus.PAYLOAD_TOO_LARGE,
                    "https://syncnest.dev/problems/payload-too-large",
                    "Payload Too Large",
                    detail);
        }
    }

    /** 415 Unsupported Media Type – Content-Type not supported. */
    public static final class UnsupportedMediaType extends ApiException {
        public UnsupportedMediaType(String detail) {
            super(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "https://syncnest.dev/problems/unsupported-media-type",
                    "Unsupported Media Type",
                    detail);
        }
    }

    /** 405 Method Not Allowed – HTTP method not supported on this endpoint. */
    public static final class MethodNotAllowed extends ApiException {
        public MethodNotAllowed(String detail) {
            super(HttpStatus.METHOD_NOT_ALLOWED,
                    "https://syncnest.dev/problems/method-not-allowed",
                    "Method Not Allowed",
                    detail);
        }
    }

    /**
     * 422 Unprocessable Entity – Validation failed after request was parsed.
     * Use when field-level/semantic validation fails (e.g., business rules).
     */
    public static final class ValidationFailed extends ApiException {
        public ValidationFailed(String detail) {
            super(HttpStatus.UNPROCESSABLE_ENTITY,
                    "https://syncnest.dev/problems/validation-failed",
                    "Validation Failed",
                    detail);
        }
    }

    /** 429 Too Many Requests – Client breached request limits applied by this service. */
    public static final class RateLimited extends ApiException {
        public RateLimited(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/rate-limited",
                    "Too Many Requests",
                    detail);
        }
    }

    /** 412 Precondition Failed – If-Match/If-Unmodified-Since etc. mismatched. */
    public static final class PreconditionFailed extends ApiException {
        public PreconditionFailed(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/precondition-failed",
                    "Precondition Failed",
                    detail);
        }
    }
}
