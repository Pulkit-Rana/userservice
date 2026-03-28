package com.syncnest.userservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for formatting error responses with error codes and detailed reasons.
 * Follows RFC 7807 Problem Details for HTTP APIs with additional error metadata.
 */
@Slf4j
public class ErrorResponseFormatter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Format a comprehensive error response with error code, title, detail, and other metadata.
     *
     * @param errorCode Machine-readable error code (e.g., AUTH_001)
     * @param title Short human-readable title
     * @param detail Detailed failure reason
     * @param type RFC 7807 problem type URI
     * @param instance The resource URL where error occurred
     * @return JSON string formatted error response
     */
    public static String formatErrorResponse(String errorCode, String title, String detail, 
                                             String type, String instance) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            
            // RFC 7807 fields
            response.put("type", type);
            response.put("title", title);
            response.put("detail", detail);
            response.put("instance", instance);
            
            // Additional error metadata
            response.put("errorCode", errorCode);
            response.put("timestamp", Instant.now().toString());
            response.put("status", 400); // Will be overridden by HTTP status
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to format error response", e);
            return "{\"type\":\"about:blank\",\"title\":\"Internal Server Error\",\"detail\":\"An unexpected error occurred\"}";
        }
    }

    /**
     * Format validation error response with field-specific failures.
     *
     * @param errorCode Error code (typically VALIDATION_*)
     * @param fieldName The field that failed validation
     * @param reason Reason for validation failure
     * @param instance The resource URL
     * @return JSON string formatted error response
     */
    public static String formatValidationError(String errorCode, String fieldName, String reason, String instance) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "https://syncnest.dev/problems/validation-error");
            response.put("title", "Validation Error");
            response.put("detail", "Field '" + fieldName + "': " + reason);
            response.put("instance", instance);
            response.put("errorCode", errorCode);
            response.put("field", fieldName);
            response.put("timestamp", Instant.now().toString());
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to format validation error response", e);
            return "{\"type\":\"about:blank\",\"title\":\"Validation Error\",\"detail\":\"Request validation failed\"}";
        }
    }

    /**
     * Format rate limit error response with retry information.
     *
     * @param errorCode Error code (typically OTP_005, OTP_006, TOKEN_007, etc.)
     * @param title Error title
     * @param detail Error detail with retry instructions
     * @param retryAfterSeconds Number of seconds to wait before retrying
     * @param instance The resource URL
     * @return JSON string formatted error response
     */
    public static String formatRateLimitError(String errorCode, String title, String detail,
                                              int retryAfterSeconds, String instance) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "https://syncnest.dev/problems/rate-limited");
            response.put("title", title);
            response.put("detail", detail);
            response.put("instance", instance);
            response.put("errorCode", errorCode);
            response.put("retryAfterSeconds", retryAfterSeconds);
            response.put("timestamp", Instant.now().toString());
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to format rate limit error response", e);
            return "{\"type\":\"about:blank\",\"title\":\"Rate Limited\",\"detail\":\"Too many requests\"}";
        }
    }

    /**
     * Format authentication failure response with security context.
     *
     * @param errorCode Error code (typically AUTH_*)
     * @param title Error title
     * @param detail Error detail
     * @param instance The resource URL
     * @param attemptCount Number of failed attempts (if applicable)
     * @return JSON string formatted error response
     */
    public static String formatAuthError(String errorCode, String title, String detail,
                                         String instance, Integer attemptCount) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "https://syncnest.dev/problems/authentication-error");
            response.put("title", title);
            response.put("detail", detail);
            response.put("instance", instance);
            response.put("errorCode", errorCode);
            if (attemptCount != null) {
                response.put("failedAttempts", attemptCount);
            }
            response.put("timestamp", Instant.now().toString());
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to format auth error response", e);
            return "{\"type\":\"about:blank\",\"title\":\"Authentication Error\",\"detail\":\"Authentication failed\"}";
        }
    }

    /**
     * Format external service error response with upstream information.
     *
     * @param errorCode Error code (typically EXTERNAL_*)
     * @param title Error title
     * @param detail Error detail
     * @param upstreamService Name of the upstream service
     * @param instance The resource URL
     * @return JSON string formatted error response
     */
    public static String formatExternalError(String errorCode, String title, String detail,
                                            String upstreamService, String instance) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "https://syncnest.dev/problems/external-error");
            response.put("title", title);
            response.put("detail", detail);
            response.put("instance", instance);
            response.put("errorCode", errorCode);
            response.put("upstreamService", upstreamService);
            response.put("timestamp", Instant.now().toString());
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to format external error response", e);
            return "{\"type\":\"about:blank\",\"title\":\"External Service Error\",\"detail\":\"External service unavailable\"}";
        }
    }
}

