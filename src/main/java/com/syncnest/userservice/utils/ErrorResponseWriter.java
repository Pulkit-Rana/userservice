package com.syncnest.userservice.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Writes a consistent RFC 7807 ProblemDetails JSON error response.
 * - Adds timestamp, path, requestId
 * - Sets no-store headers for auth-related responses
 * - Uses application/problem+json content type
 */
@Component
public class ErrorResponseWriter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(@NonNull HttpServletRequest req,
                      @NonNull HttpServletResponse resp,
                      @NonNull HttpStatus status,
                      String type,              // e.g. "https://syncnest.dev/problems/unauthorized"
                      @NonNull String title,
                      @NonNull String detail) throws IOException {

        if (resp.isCommitted()) return;

        ProblemDetail pd = ProblemDetail.forStatus(status);
        if (type != null && !type.isBlank()) {
            pd.setType(URI.create(type));
        }
        pd.setTitle(title);
        pd.setDetail(detail);

        // Common context
        pd.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        pd.setProperty("path", req.getRequestURI());
        pd.setProperty("requestId", resp.getHeader(REQUEST_ID_HEADER));

        // No-store for auth errors and in general for error payloads
        resp.setStatus(status.value());
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/problem+json");

        objectMapper.writeValue(resp.getWriter(), pd);
    }
}
