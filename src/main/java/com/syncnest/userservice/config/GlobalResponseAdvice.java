package com.syncnest.userservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncnest.userservice.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps every successful 2xx response in the standard {@link ApiResponse} envelope.
 *
 * Responsibility: response wrapping ONLY.
 * All exception handling lives exclusively in {@link com.syncnest.userservice.exception.GlobalExceptionHandler}.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    private static final String REQ_ID_HEADER = "X-Request-Id";
    private static final String REQ_ID_ATTR   = "SYNCNEST_REQUEST_ID";

    // -------------------------------------------------------------------------
    // ResponseBodyAdvice — wrapping
    // -------------------------------------------------------------------------

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // decide in beforeBodyWrite (need media type / body / status)
        return true;
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @Nullable MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        String path   = request.getURI().getPath();
        String method = request.getMethod().name();

        log.debug("Response wrapping for {} {} - ContentType: {}", method, path, selectedContentType);

        // skip RFC 7807 problem responses — these come from GlobalExceptionHandler
        if (body instanceof ProblemDetail) {
            log.debug("Skipping ProblemDetail response");
            return body;
        }

        // skip swagger / openapi
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger")) {
            log.debug("Skipping swagger/openapi path");
            return body;
        }

        // only JSON-like content types
        if (selectedContentType != null && !isJsonLike(selectedContentType)) {
            log.debug("Skipping non-JSON content type: {}", selectedContentType);
            return body;
        }

        // already wrapped — avoid double-wrapping
        if (body instanceof ApiResponse<?>) {
            log.debug("Response already wrapped, skipping");
            return body;
        }

        ResolvedAnn ann = resolveAnn(returnType);

        // ResponseEntity: preserve status + headers; wrap 2xx non-null non-204 bodies only
        if (body instanceof ResponseEntity<?> re) {
            HttpStatusCode sc = re.getStatusCode();
            log.debug("ResponseEntity with status: {}", sc);

            if (!sc.is2xxSuccessful() || sc.equals(HttpStatus.NO_CONTENT)) {
                log.debug("Non-2xx or 204 status, skipping wrap");
                return body;
            }

            Object inner = re.getBody();
            if (inner instanceof ApiResponse<?> || inner instanceof ProblemDetail) return body;
            if (shouldSkip(inner, selectedContentType))                            return body;

            if (inner instanceof MappingJacksonValue mjv) {
                Object wrapped = buildEnvelope(mjv.getValue(), ann, request, response);
                mjv.setValue(wrapped);
                return ResponseEntity.status(sc).headers(re.getHeaders()).body(mjv);
            }

            Object wrapped = buildEnvelope(inner, ann, request, response);
            log.info("Wrapped ResponseEntity for {} {} - Status: {}", method, path, sc);
            return ResponseEntity.status(sc).headers(re.getHeaders()).body(wrapped);
        }

        // non-ResponseEntity: inspect current status
        HttpStatus current = currentStatus(response);
        if (!current.is2xxSuccessful() || current == HttpStatus.NO_CONTENT) {
            log.debug("Non-2xx or no-content status ({}), skipping wrap", current);
            return body;
        }

        // honour @ResponseMessage http status override
        if (ann.httpStatus != null && response instanceof ServletServerHttpResponse sResp) {
            sResp.getServletResponse().setStatus(ann.httpStatus);
            HttpStatus overridden = HttpStatus.valueOf(ann.httpStatus);
            if (!overridden.is2xxSuccessful() || overridden == HttpStatus.NO_CONTENT) return body;
        }

        // String return types need explicit JSON serialisation
        if (returnType.getParameterType() == String.class) {
            try {
                log.debug("Wrapping String return type as JSON");
                return objectMapper.writeValueAsString(buildEnvelope(body, ann, request, response));
            } catch (Exception e) {
                log.warn("Failed to serialise ApiResponse for String return type: {}", e.toString());
                return body;
            }
        }

        if (shouldSkip(body, selectedContentType)) {
            if (body == null) return buildEnvelope(null, ann, request, response);
            log.debug("Skipping file/stream response");
            return body;
        }

        if (body instanceof MappingJacksonValue mjv) {
            Object wrapped = buildEnvelope(mjv.getValue(), ann, request, response);
            mjv.setValue(wrapped);
            return mjv;
        }

        Object finalWrapped = buildEnvelope(body, ann, request, response);
        log.info("Wrapped response for {} {} - BodyType: {}",
                method, path, body != null ? body.getClass().getSimpleName() : "null");
        return finalWrapped;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Object buildEnvelope(@Nullable Object body,
                                 ResolvedAnn ann,
                                 ServerHttpRequest request,
                                 ServerHttpResponse response) {

        Map<String, Object> meta = new LinkedHashMap<>(2);
        String reqId = requestId(request, response);
        if (StringUtils.hasText(reqId)) meta.put("requestId", reqId);

        Object pageMeta = pageMeta(body);
        meta = mergeMeta(pageMeta, meta);

        return ApiResponse.builder()
                .success(ann.success)
                .code(ann.code)
                .message(ann.message)
                .data(body)
                .meta(meta.isEmpty() ? null : meta)
                .timestamp(Instant.now())
                .build();
    }

    private boolean shouldSkip(@Nullable Object body, @Nullable MediaType mt) {
        if (body == null) return false;
        if (body instanceof ApiResponse<?>)   return true;
        if (body instanceof byte[])           return true;
        if (body instanceof org.springframework.core.io.Resource) return true;
        if (body instanceof org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody) return true;
        if (body instanceof String && mt != null
                && !MediaType.APPLICATION_JSON.includes(mt)
                && !mt.getSubtype().endsWith("+json")) {
            return true;
        }
        return false;
    }

    private boolean isJsonLike(@NonNull MediaType mediaType) {
        if (MediaType.APPLICATION_PROBLEM_JSON.includes(mediaType)) return false;
        if (MediaType.TEXT_EVENT_STREAM.includes(mediaType))        return false;
        if (MediaType.APPLICATION_OCTET_STREAM.includes(mediaType)) return false;
        return MediaType.APPLICATION_JSON.includes(mediaType)
                || mediaType.getSubtype().endsWith("+json");
    }

    private HttpStatus currentStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse sResp) {
            HttpStatus resolved = HttpStatus.resolve(sResp.getServletResponse().getStatus());
            if (resolved != null) return resolved;
        }
        return HttpStatus.OK;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMeta(@Nullable Object a, @Nullable Map<String, Object> b) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (a instanceof Map<?, ?> am) am.forEach((k, v) -> out.put(String.valueOf(k), v));
        if (b != null) out.putAll(b);
        return out;
    }

    private Object pageMeta(@Nullable Object body) {
        if (body instanceof org.springframework.data.domain.Page<?> page) {
            var map = new LinkedHashMap<String, Object>(5);
            map.put("page",       page.getNumber());
            map.put("size",       page.getSize());
            map.put("totalItems", page.getTotalElements());
            map.put("totalPages", page.getTotalPages());
            var sort = page.getSort();
            if (sort.isSorted()) map.put("sort", sort.toString());
            return map;
        }
        return null;
    }

    private String requestId(@NonNull ServerHttpRequest req, @NonNull ServerHttpResponse resp) {
        String id = req.getHeaders().getFirst(REQ_ID_HEADER);
        if (!StringUtils.hasText(id)) id = resp.getHeaders().getFirst(REQ_ID_HEADER);
        if (!StringUtils.hasText(id) && req instanceof ServletServerHttpRequest sreq) {
            Object attr = sreq.getServletRequest().getAttribute(REQ_ID_ATTR);
            if (attr instanceof String s && StringUtils.hasText(s)) id = s;
        }
        return id;
    }

    // ---- @ResponseMessage annotation support --------------------------------

    @Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE})
    @Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @Documented
    public @interface ResponseMessage {
        String value() default "OK";
        String code() default "OK";
        int httpStatus() default 200;
        boolean success() default true;
    }

    private ResolvedAnn resolveAnn(@NonNull MethodParameter p) {
        ResponseMessage ann = p.getMethodAnnotation(ResponseMessage.class);
        if (ann == null) ann = p.getContainingClass().getAnnotation(ResponseMessage.class);

        String  message    = (ann != null && StringUtils.hasText(ann.value())) ? ann.value() : "OK";
        String  code       = (ann != null && StringUtils.hasText(ann.code()))  ? ann.code()  : "OK";
        boolean success    = ann == null || ann.success();
        Integer httpStatus = (ann != null && ann.httpStatus() > 0)             ? ann.httpStatus() : null;

        return new ResolvedAnn(message, code, success, httpStatus, p.getMethod());
    }

    private record ResolvedAnn(String message, String code, boolean success, Integer httpStatus, Method source) {}
}