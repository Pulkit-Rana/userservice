package com.syncnest.userservice.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class ErrorResponseWriter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_ATTR   = "SYNCNEST_REQUEST_ID";

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

        // RFC 7807 recommended fields
        pd.setInstance(URI.create(req.getRequestURI()));

        // Common context
        pd.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        pd.setProperty("path", req.getRequestURI());
        pd.setProperty("requestId", resolveRequestId(req, resp));

        // Headers
        resp.setStatus(status.value());
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/problem+json");

        // Optional: per-status header hints
        // if (status == HttpStatus.UNAUTHORIZED && resp.getHeader("WWW-Authenticate") == null) {
        //     resp.setHeader("WWW-Authenticate", "Bearer");
        // }
        // resp.addHeader("Vary", "Authorization");

        objectMapper.writeValue(resp.getOutputStream(), pd);
    }

    private String resolveRequestId(HttpServletRequest req, HttpServletResponse resp) {
        String id = resp.getHeader(REQUEST_ID_HEADER);
        if (id == null || id.isBlank()) {
            id = req.getHeader(REQUEST_ID_HEADER);
        }
        if ((id == null || id.isBlank())) {
            Object attr = req.getAttribute(REQUEST_ID_ATTR);
            if (attr instanceof String s && !s.isBlank()) {
                id = s;
            }
        }
        return id;
    }
}
