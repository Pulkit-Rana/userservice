package com.syncnest.userservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncnest.userservice.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    private static final String REQ_ID_HEADER = "X-Request-Id";
    private static final String REQ_ID_ATTR   = "SYNCNEST_REQUEST_ID";

    // --------------- Exception handling (centralized) ----------------

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> onBadCreds(Exception ex, HttpServletRequest req) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ApiResponse.<Void>builder()
                .success(false).code("UNAUTHORIZED").message("Invalid email or password")
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> onValidation(MethodArgumentNotValidException ex) {
        return ApiResponse.<Void>builder()
                .success(false).code("VALIDATION_ERROR").message("Invalid request")
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> onAny(Exception ex) {
        log.error("Server error", ex);
        return ApiResponse.<Void>builder()
                .success(false).code("INTERNAL_ERROR").message("Something went wrong")
                .timestamp(Instant.now())
                .build();
    }

    // --------------- Response wrapping ----------------

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // decide in beforeBodyWrite (need media type/body/status)
        return true;
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @Nullable MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        // skip RFC 7807
        if (body instanceof ProblemDetail) return body;

        // skip swagger/openapi
        String path = request.getURI().getPath();
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger") || path.startsWith("/swagger-ui")) {
            return body;
        }

        // only JSON-like
        if (selectedContentType != null && !isJsonLike(selectedContentType)) {
            return body;
        }

        // already wrapped
        if (body instanceof ApiResponse<?>) return body;

        // resolve @ResponseMessage (if present)
        var ann = resolveAnn(returnType);

        // ResponseEntity: preserve status/headers; wrap only 2xx & non-null (not 204)
        if (body instanceof ResponseEntity<?> re) {
            HttpStatusCode sc = re.getStatusCode();
            if (!sc.is2xxSuccessful()) return body;
            if (sc.equals(HttpStatus.NO_CONTENT)) return body;

            Object inner = re.getBody();
            if (inner instanceof ApiResponse<?> || inner instanceof ProblemDetail) return body;
            if (shouldSkip(inner, selectedContentType)) return body;

            // MappingJacksonValue passthrough
            if (inner instanceof MappingJacksonValue mjv) {
                Object wrapped = buildEnvelope(mjv.getValue(), ann, request, response);
                mjv.setValue(wrapped);
                return ResponseEntity.status(sc).headers(re.getHeaders()).body(mjv);
            }

            Object wrapped = buildEnvelope(inner, ann, request, response);
            return ResponseEntity.status(sc).headers(re.getHeaders()).body(wrapped);
        }

        // non-ResponseEntity: get current status
        HttpStatus current = currentStatus(response);
        if (!current.is2xxSuccessful() || current == HttpStatus.NO_CONTENT) return body;

        // allow @ResponseMessage to override 2xx status
        if (ann.httpStatus != null && response instanceof ServletServerHttpResponse sResp) {
            sResp.getServletResponse().setStatus(ann.httpStatus);
            HttpStatus overridden = HttpStatus.valueOf(ann.httpStatus);
            if (!overridden.is2xxSuccessful() || overridden == HttpStatus.NO_CONTENT) return body;
        }

        // String return types need explicit JSON string
        if (returnType.getParameterType() == String.class) {
            try {
                return objectMapper.writeValueAsString(buildEnvelope(body, ann, request, response));
            } catch (Exception e) {
                log.warn("Failed to serialize ApiResponse for String return type: {}", e.toString());
                return body; // fail open
            }
        }

        // skip files/streams; but wrap null as data=null (unless 204 which we handled)
        if (shouldSkip(body, selectedContentType)) {
            if (body == null) {
                return buildEnvelope(null, ann, request, response);
            }
            return body;
        }

        // MappingJacksonValue passthrough
        if (body instanceof MappingJacksonValue mjv) {
            Object wrapped = buildEnvelope(mjv.getValue(), ann, request, response);
            mjv.setValue(wrapped);
            return mjv;
        }

        return buildEnvelope(body, ann, request, response);
    }

    // --------------- helpers ----------------

    /** build final ApiResponse and attach meta (requestId + pagination if Page<?>). */
    private Object buildEnvelope(@Nullable Object body,
                                 ResolvedAnn ann,
                                 ServerHttpRequest request,
                                 ServerHttpResponse response) {

        Map<String, Object> meta = new LinkedHashMap<>(2);
        String reqId = requestId(request, response);
        if (StringUtils.hasText(reqId)) meta.put("requestId", reqId);

        Object pageMeta = pageMeta(body);
        meta = mergeMeta(pageMeta, meta);

        // use builder so we can apply success/code/message from annotation
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
        if (body == null) return false; // wrap null as data=null
        if (body instanceof ApiResponse<?>) return true;
        if (body instanceof byte[]) return true;
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
        // exclude problem+json, sse, octet-stream
        if (MediaType.APPLICATION_PROBLEM_JSON.includes(mediaType)) return false;
        if (MediaType.TEXT_EVENT_STREAM.includes(mediaType)) return false;
        if (MediaType.APPLICATION_OCTET_STREAM.includes(mediaType)) return false;
        return MediaType.APPLICATION_JSON.includes(mediaType) ||
                mediaType.getSubtype().endsWith("+json");
    }

    private HttpStatus currentStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse sResp) {
            HttpStatus resolved = HttpStatus.resolve(sResp.getServletResponse().getStatus());
            if (resolved != null) return resolved;
        }
        return HttpStatus.OK;
    }

    /** merge page meta (if any) with base meta map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMeta(@Nullable Object a, @Nullable Map<String, Object> b) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (a instanceof Map<?, ?> am) {
            am.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        if (b != null) out.putAll(b);
        return out;
    }

    private Object pageMeta(@Nullable Object body) {
        if (body instanceof org.springframework.data.domain.Page<?> page) {
            var map = new LinkedHashMap<String, Object>(5);
            map.put("page", page.getNumber());
            map.put("size", page.getSize());
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

    // ---- @ResponseMessage support ----
    // define your annotation in the same package or import it
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

        String message = (ann != null && StringUtils.hasText(ann.value())) ? ann.value() : "OK";
        String code    = (ann != null && StringUtils.hasText(ann.code()))  ? ann.code()  : "OK";
        boolean success = (ann != null) ? ann.success() : true;

        Integer httpStatus = null;
        if (ann != null && ann.httpStatus() > 0) httpStatus = ann.httpStatus();

        Method source = p.getMethod();
        return new ResolvedAnn(message, code, success, httpStatus, source);
    }

    private record ResolvedAnn(String message, String code, boolean success, Integer httpStatus, Method source) {}
}
