package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Typed exceptions for failures caused by external/third-party systems.
 * These extend ApiException so GlobalExceptionHandler will render RFC7807 ProblemDetails.
 *
 * All exceptions include:
 *  - Error code: Machine-readable code (EXTERNAL_*, SYSTEM_*)
 *  - Detailed reason: Specific description of external failure
 *  - HTTP Status: Appropriate HTTP status code
 *  - Type: RFC 7807 problem type URI
 *
 * Suggested usage:
 * - Throw from adapters/clients that call external services.
 * - Wrap low-level I/O/HTTP exceptions with an appropriate subclass.
 */
public final class ExternalExceptions {

    private ExternalExceptions() {}

    /** 502 Bad Gateway – Upstream returned an invalid or unexpected response. */
    public static final class UpstreamBadGateway extends ApiException {
        public UpstreamBadGateway(String detail) {
            super(HttpStatus.BAD_GATEWAY,
                    "https://syncnest.dev/problems/upstream-bad-gateway",
                    "Upstream Bad Gateway",
                    ErrorCode.EXTERNAL_005,
                    detail != null ? detail : ErrorCode.EXTERNAL_005.getMessage());
        }
    }

    /** 503 Service Unavailable – Upstream is unavailable (maintenance/overload). */
    public static final class UpstreamUnavailable extends ApiException {
        public UpstreamUnavailable(String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE,
                    "https://syncnest.dev/problems/upstream-unavailable",
                    "Upstream Unavailable",
                    ErrorCode.EXTERNAL_003,
                    detail != null ? detail : ErrorCode.EXTERNAL_003.getMessage());
        }
    }

    /** 504 Gateway Timeout – Upstream did not respond in time. */
    public static final class UpstreamTimeout extends ApiException {
        public UpstreamTimeout(String detail) {
            super(HttpStatus.GATEWAY_TIMEOUT,
                    "https://syncnest.dev/problems/upstream-timeout",
                    "Upstream Timeout",
                    ErrorCode.EXTERNAL_004,
                    detail != null ? detail : ErrorCode.EXTERNAL_004.getMessage());
        }
    }

    /** 429 Too Many Requests – Upstream rate limited our request. */
    public static final class ExternalRateLimited extends ApiException {
        public ExternalRateLimited(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/external-rate-limited",
                    "External Rate Limited",
                    ErrorCode.EXTERNAL_010,
                    detail != null ? detail : ErrorCode.EXTERNAL_010.getMessage());
        }
    }

    /** 401 Unauthorized – Upstream authentication/credentials failed. */
    public static final class ExternalAuthFailed extends ApiException {
        public ExternalAuthFailed(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/external-auth-failed",
                    "External Authentication Failed",
                    ErrorCode.EXTERNAL_007,
                    detail != null ? detail : "Failed to authenticate with external service");
        }
    }

    /** 400 Bad Request – Upstream rejected our request as invalid. */
    public static final class ExternalBadRequest extends ApiException {
        public ExternalBadRequest(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/external-bad-request",
                    "External Bad Request",
                    ErrorCode.EXTERNAL_009,
                    detail != null ? detail : "External service rejected the request as invalid");
        }
    }

    /**
     * 422 Unprocessable Entity – Upstream accepted the request format but
     * could not process the semantics (e.g., provider-specific validation).
     */
    public static final class ExternalUnprocessable extends ApiException {
        public ExternalUnprocessable(String detail) {
            super(HttpStatus.UNPROCESSABLE_ENTITY,
                    "https://syncnest.dev/problems/external-unprocessable",
                    "External Unprocessable",
                    ErrorCode.EXTERNAL_009,
                    detail != null ? detail : "External service could not process the request");
        }
    }

    /**
     * 502 – Unexpected/invalid payload/contract change from upstream.
     * Prefer UpstreamBadGateway; this is a more explicit name when schema is violated.
     */
    public static final class ExternalContractViolation extends ApiException {
        public ExternalContractViolation(String detail) {
            super(HttpStatus.BAD_GATEWAY,
                    "https://syncnest.dev/problems/external-contract-violation",
                    "External Contract Violation",
                    ErrorCode.EXTERNAL_005,
                    detail != null ? detail : "External service response violated expected contract");
        }
    }

    /** 503 Service Unavailable – Email service is unavailable. */
    public static final class EmailServiceUnavailable extends ApiException {
        public EmailServiceUnavailable(String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE,
                    "https://syncnest.dev/problems/email-service-unavailable",
                    "Email Service Unavailable",
                    ErrorCode.EXTERNAL_001,
                    detail != null ? detail : ErrorCode.EXTERNAL_001.getMessage());
        }
    }

    /** 500 Internal Server Error – Email delivery failed. */
    public static final class EmailDeliveryFailed extends ApiException {
        public EmailDeliveryFailed(String detail) {
            super(HttpStatus.INTERNAL_SERVER_ERROR,
                    "https://syncnest.dev/problems/email-delivery-failed",
                    "Email Delivery Failed",
                    ErrorCode.EXTERNAL_002,
                    detail != null ? detail : ErrorCode.EXTERNAL_002.getMessage());
        }
    }

    /** 502 Bad Gateway – Geolocation service error. */
    public static final class GeolocationServiceError extends ApiException {
        public GeolocationServiceError(String detail) {
            super(HttpStatus.BAD_GATEWAY,
                    "https://syncnest.dev/problems/geolocation-error",
                    "Geolocation Service Error",
                    ErrorCode.EXTERNAL_006,
                    detail != null ? detail : ErrorCode.EXTERNAL_006.getMessage());
        }
    }
}
