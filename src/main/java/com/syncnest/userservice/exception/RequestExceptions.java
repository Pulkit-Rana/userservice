package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceptions representing problems with the incoming client request itself
 * (malformed input, invalid state, headers/media, etc.).
 *
 * All exceptions include:
 *  - Error code: Machine-readable code (VALIDATION_*, SYSTEM_*, etc.)
 *  - Detailed reason: Specific description of what went wrong
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 */
public final class RequestExceptions {

    private RequestExceptions() {}

    /** 400 Bad Request – Request is syntactically incorrect or semantically invalid (generic). */
    public static final class BadRequest extends ApiException {
        public BadRequest(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/bad-request",
                    "Bad Request",
                    ErrorCode.SYSTEM_007,
                    detail != null ? detail : ErrorCode.SYSTEM_007.getMessage());
        }
    }

    /** 400 Bad Request – A required parameter is missing/blank/invalid. */
    public static final class InvalidParameter extends ApiException {
        public InvalidParameter(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-parameter",
                    "Invalid Parameter",
                    ErrorCode.VALIDATION_002,
                    detail != null ? detail : ErrorCode.VALIDATION_002.getMessage());
        }
    }

    /** 400 Bad Request – Required header is missing or invalid. */
    public static final class InvalidHeader extends ApiException {
        public InvalidHeader(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-header",
                    "Invalid Header",
                    ErrorCode.VALIDATION_002,
                    detail != null ? detail : "Required header is missing or invalid");
        }
    }

    /** 413 Payload Too Large – Body exceeds configured limits. */
    public static final class PayloadTooLarge extends ApiException {
        public PayloadTooLarge(String detail) {
            super(HttpStatus.PAYLOAD_TOO_LARGE,
                    "https://syncnest.dev/problems/payload-too-large",
                    "Payload Too Large",
                    ErrorCode.SYSTEM_008,
                    detail != null ? detail : "Request body exceeds maximum allowed size");
        }
    }

    /** 415 Unsupported Media Type – Content-Type not supported. */
    public static final class UnsupportedMediaType extends ApiException {
        public UnsupportedMediaType(String detail) {
            super(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "https://syncnest.dev/problems/unsupported-media-type",
                    "Unsupported Media Type",
                    ErrorCode.SYSTEM_006,
                    detail != null ? detail : ErrorCode.SYSTEM_006.getMessage());
        }
    }

    /** 405 Method Not Allowed – HTTP method not supported on this endpoint. */
    public static final class MethodNotAllowed extends ApiException {
        public MethodNotAllowed(String detail) {
            super(HttpStatus.METHOD_NOT_ALLOWED,
                    "https://syncnest.dev/problems/method-not-allowed",
                    "Method Not Allowed",
                    ErrorCode.SYSTEM_005,
                    detail != null ? detail : ErrorCode.SYSTEM_005.getMessage());
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
                    ErrorCode.VALIDATION_001,
                    detail != null ? detail : ErrorCode.VALIDATION_001.getMessage());
        }
    }

    /** 429 Too Many Requests – Client breached request limits applied by this service. */
    public static final class RateLimited extends ApiException {
        public RateLimited(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/rate-limited",
                    "Too Many Requests",
                    ErrorCode.SYSTEM_008,
                    detail != null ? detail : "Rate limit exceeded. Please try again later");
        }
    }

    /** 412 Precondition Failed – If-Match/If-Unmodified-Since etc. mismatched. */
    public static final class PreconditionFailed extends ApiException {
        public PreconditionFailed(String detail) {
            super(HttpStatus.PRECONDITION_FAILED,
                    "https://syncnest.dev/problems/precondition-failed",
                    "Precondition Failed",
                    ErrorCode.VALIDATION_010,
                    detail != null ? detail : "Request preconditions are not met");
        }
    }

    /** 400 Bad Request – Missing required field in request. */
    public static final class MissingRequiredField extends ApiException {
        public MissingRequiredField(String fieldName) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/missing-required-field",
                    "Missing Required Field",
                    ErrorCode.VALIDATION_003,
                    "Required field '" + fieldName + "' is missing");
        }
    }

    /** 400 Bad Request – Field length validation failed. */
    public static final class FieldLengthExceeded extends ApiException {
        public FieldLengthExceeded(String fieldName, int maxLength) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/field-length-exceeded",
                    "Field Length Exceeded",
                    ErrorCode.VALIDATION_005,
                    "Field '" + fieldName + "' exceeds maximum length of " + maxLength + " characters");
        }
    }

    /** 400 Bad Request – Invalid enum value. */
    public static final class InvalidEnumValue extends ApiException {
        public InvalidEnumValue(String fieldName, String value) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/invalid-enum-value",
                    "Invalid Enum Value",
                    ErrorCode.VALIDATION_006,
                    "Field '" + fieldName + "' has invalid value: '" + value + "'");
        }
    }
}
