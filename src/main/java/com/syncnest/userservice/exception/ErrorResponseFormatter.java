package com.syncnest.userservice.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formats RFC 7807-compliant JSON error response bodies.
 *
 * Rules:
 *  - {@code status} always reflects the real HTTP status code passed in — never hardcoded.
 *  - No {@code errorCode} field is exposed to clients (internal concern only).
 */
@Slf4j
public class ErrorResponseFormatter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ErrorResponseFormatter() {}

    /**
     * Builds a RFC 7807 Problem Details JSON string.
     *
     * @param status   the real HTTP status that will be sent on the wire
     * @param type     problem type URI  (e.g. "https://syncnest.dev/problems/user-already-exists")
     * @param title    short human-readable title  (e.g. "User Already Exists")
     * @param detail   specific reason  (e.g. "An account with email 'p***@gmail.com' already exists.")
     * @param instance the request URI  (e.g. "/api/v1/auth/register")
     */
    public static String formatErrorResponse(HttpStatus status,
                                             String type,
                                             String title,
                                             String detail,
                                             String instance) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",      type);
            body.put("title",     title);
            body.put("detail",    detail);
            body.put("instance",  instance);
            body.put("status",    status.value());   // ← real status, never hardcoded
            body.put("timestamp", Instant.now().toString());
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Failed to format error response", e);
            return fallback(status);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String fallback(HttpStatus status) {
        return "{\"type\":\"about:blank\",\"title\":\"Internal Server Error\","
                + "\"detail\":\"An unexpected error occurred.\","
                + "\"status\":" + status.value() + "}";
    }
}