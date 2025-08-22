package com.syncnest.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Typed exceptions for failures caused by external/third-party systems.
 * These extend ApiException so GlobalExceptionHandler will render RFC7807 ProblemDetails.
 *
 * Conventions:
 * - type:    https://syncnest.dev/problems/<slug>
 * - title:   short human-readable summary
 * - detail:  free-form (safe) message; avoid sensitive payloads
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
                    detail);
        }
    }

    /** 503 Service Unavailable – Upstream is unavailable (maintenance/overload). */
    public static final class UpstreamUnavailable extends ApiException {
        public UpstreamUnavailable(String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE,
                    "https://syncnest.dev/problems/upstream-unavailable",
                    "Upstream Unavailable",
                    detail);
        }
    }

    /** 504 Gateway Timeout – Upstream did not respond in time. */
    public static final class UpstreamTimeout extends ApiException {
        public UpstreamTimeout(String detail) {
            super(HttpStatus.GATEWAY_TIMEOUT,
                    "https://syncnest.dev/problems/upstream-timeout",
                    "Upstream Timeout",
                    detail);
        }
    }

    /** 429 Too Many Requests – Upstream rate limited our request. */
    public static final class ExternalRateLimited extends ApiException {
        public ExternalRateLimited(String detail) {
            super(HttpStatus.TOO_MANY_REQUESTS,
                    "https://syncnest.dev/problems/external-rate-limited",
                    "External Rate Limited",
                    detail);
        }
    }

    /** 401 Unauthorized – Upstream authentication/credentials failed. */
    public static final class ExternalAuthFailed extends ApiException {
        public ExternalAuthFailed(String detail) {
            super(HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/external-auth-failed",
                    "External Authentication Failed",
                    detail);
        }
    }

    /** 400 Bad Request – Upstream rejected our request as invalid. */
    public static final class ExternalBadRequest extends ApiException {
        public ExternalBadRequest(String detail) {
            super(HttpStatus.BAD_REQUEST,
                    "https://syncnest.dev/problems/external-bad-request",
                    "External Bad Request",
                    detail);
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
                    detail);
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
                    detail);
        }
    }
}
