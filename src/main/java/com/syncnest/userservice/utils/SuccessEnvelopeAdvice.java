package com.syncnest.userservice.utils;

import com.syncnest.userservice.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
/**
 * Wraps successful JSON responses in ApiResponse:
 * {
 *   "timestamp": "...Z",
 *   "requestId": "...",
 *   "message": "OK",
 *   "data": {...},
 *   "meta": {...}
 * }
 * Skips wrapping:
 *  - ProblemDetail (errors)
 *  - Non-2xx ResponseEntity
 *  - Already-wrapped ApiResponse
 *  - File/stream bodies (byte[], Resource, StreamingResponseBody)
 *  - Non-JSON media types (e.g., text/html, text/event-stream, octet-stream)
 */
@RestControllerAdvice
public class SuccessEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    private static final String REQ_ID_HEADER = "X-Request-Id";
    // If you set an attribute in your interceptor, we’ll also read it:
    private static final String REQ_ID_ATTR   = "SYNCNEST_REQUEST_ID";

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // Decide in beforeBodyWrite (we need MediaType & body)
        return true;
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        // Never wrap RFC 7807 errors
        if (body instanceof ProblemDetail) return body;

        // Only wrap JSON-like responses; skip for problem+json (handled above), event-stream, octet-stream, etc.
        if (!isJsonLike(selectedContentType)) return body;

        // Handle ResponseEntity specially (preserve headers & status)
        if (body instanceof ResponseEntity<?> entity) {
            if (!entity.getStatusCode().is2xxSuccessful()) return body; // don’t wrap non-2xx
            Object inner = entity.getBody();
            if (shouldSkip(inner)) return body;
            var wrapped = ApiResponse.of(
                    requestId(request, response),
                    resolveMessage(returnType),
                    inner,
                    pageMeta(inner)
            );
            return ResponseEntity.status(entity.getStatusCode())
                    .headers(entity.getHeaders())
                    .body(wrapped);
        }

        if (shouldSkip(body)) return body;

        // Don’t wrap if the response status is not 2xx
        HttpStatus status = HttpStatus.OK;
        if (response instanceof ServletServerHttpResponse sResp) {
            int sc = sResp.getServletResponse().getStatus();
            var resolved = HttpStatus.resolve(sc);
            if (resolved != null) status = resolved;
        }
        if (!status.is2xxSuccessful()) return body;

        return ApiResponse.of(
                requestId(request, response),
                resolveMessage(returnType),
                body,
                pageMeta(body)
        );
    }

    /** Skip wrapping for nulls, already-wrapped, files/streams. */
    private boolean shouldSkip(@Nullable Object body) {
        return body == null
                || body instanceof ApiResponse<?>
                || body instanceof byte[]
                || body instanceof org.springframework.core.io.Resource
                || body instanceof org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
    }

    /** JSON-ish types we want to wrap; exclude problem+json (already handled), ndjson/event-stream. */
    private boolean isJsonLike(@NonNull MediaType mt) {
        if (MediaType.APPLICATION_PROBLEM_JSON.includes(mt)) return false;
        if (MediaType.TEXT_EVENT_STREAM.includes(mt)) return false; // SSE
        if (MediaType.APPLICATION_OCTET_STREAM.includes(mt)) return false; // binary

        // Treat */json and application/*+json as JSON-like
        return MediaType.APPLICATION_JSON.includes(mt)
                || mt.getSubtype().endsWith("+json");
    }

    /** Resolve message from @ResponseMessage on method or controller, default "OK". */
    private String resolveMessage(@NonNull MethodParameter returnType) {
        ResponseMessage ann = returnType.getMethodAnnotation(ResponseMessage.class);
        if (ann == null) {
            ann = returnType.getContainingClass().getAnnotation(ResponseMessage.class);
        }
        return (ann != null && StringUtils.hasText(ann.value())) ? ann.value() : "OK";
    }

    /** Pull requestId from header, then response header, then servlet attribute (if your interceptor set it). */
    private String requestId(@NonNull ServerHttpRequest req, @NonNull ServerHttpResponse resp) {
        String id = req.getHeaders().getFirst(REQ_ID_HEADER);
        if (!StringUtils.hasText(id)) {
            id = resp.getHeaders().getFirst(REQ_ID_HEADER);
        }
        if (!StringUtils.hasText(id) && req instanceof ServletServerHttpRequest sreq) {
            Object attr = sreq.getServletRequest().getAttribute(REQ_ID_ATTR);
            if (attr instanceof String s && StringUtils.hasText(s)) id = s;
        }
        return id;
    }

    /** Add pagination meta if the body is a Page<?>. Safe no-op otherwise. */
    private @Nullable Object pageMeta(@Nullable Object body) {
        if (body instanceof org.springframework.data.domain.Page<?> page) {
            var map = new java.util.LinkedHashMap<String, Object>(5);
            map.put("page", page.getNumber());
            map.put("size", page.getSize());
            map.put("totalItems", page.getTotalElements());
            map.put("totalPages", page.getTotalPages());

            var sort = page.getSort();
            if (sort.isSorted()) {
                map.put("sort", sort.toString());
            }
            return map;
        }
        return null;
    }
}
