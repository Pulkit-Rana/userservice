package com.syncnest.userservice.exception;

import com.syncnest.userservice.utils.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Single source of truth for all exception handling.
 *
 * Responsibility: exceptions ONLY.
 * Response wrapping for successful responses lives in
 * {@link com.syncnest.userservice.config.GlobalResponseAdvice}.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorResponseWriter writer;

    // -------------------------------------------------------------------------
    // 1. Custom domain / API exceptions  (ApiException hierarchy)
    // -------------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public void handleApiException(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse resp,
                                   @NonNull ApiException ex) throws IOException {
        log.warn("ApiException: status={}, title={}, detail={}",
                ex.getStatus(), ex.getTitle(), ex.getDetailReason());

        // Pass the real status so the body's "status" field always matches the HTTP status line
        String body = ErrorResponseFormatter.formatErrorResponse(
                ex.getStatus(),          // ← real status (e.g. 409, 401, 404 …)
                ex.getType(),
                ex.getTitle(),
                ex.getDetailReason(),
                req.getRequestURI()
        );
        writer.writeJson(req, resp, ex.getStatus(), body);
    }

    // -------------------------------------------------------------------------
    // 2. Spring Security exceptions
    // -------------------------------------------------------------------------

    @ExceptionHandler(BadCredentialsException.class)
    public void handleBadCredentials(@NonNull HttpServletRequest req,
                                     @NonNull HttpServletResponse resp,
                                     @NonNull BadCredentialsException ex) throws IOException {
        log.warn("BadCredentialsException: {} - path={}", ex.getMessage(), req.getRequestURI());
        writer.write(req, resp,
                HttpStatus.UNAUTHORIZED,
                "https://syncnest.dev/problems/invalid-credentials",
                "Invalid Credentials",
                "Email or password is incorrect.");
    }

    @ExceptionHandler(DisabledException.class)
    public void handleDisabled(@NonNull HttpServletRequest req,
                               @NonNull HttpServletResponse resp,
                               @NonNull DisabledException ex) throws IOException {
        log.warn("DisabledException: {} - path={}", ex.getMessage(), req.getRequestURI());
        writer.write(req, resp,
                HttpStatus.LOCKED,
                "https://syncnest.dev/problems/account-suspended",
                "Account Suspended",
                "Your account has been suspended or disabled.");
    }

    @ExceptionHandler(LockedException.class)
    public void handleLocked(@NonNull HttpServletRequest req,
                             @NonNull HttpServletResponse resp,
                             @NonNull LockedException ex) throws IOException {
        log.warn("LockedException: {} - path={}", ex.getMessage(), req.getRequestURI());
        writer.write(req, resp,
                HttpStatus.LOCKED,
                "https://syncnest.dev/problems/account-locked",
                "Account Locked",
                "Your account is locked. Please verify your email or contact support.");
    }

    // -------------------------------------------------------------------------
    // 3. Validation errors — field-level detail exposed to the client
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleMethodArgumentNotValid(@NonNull HttpServletRequest req,
                                             @NonNull HttpServletResponse resp,
                                             @NonNull MethodArgumentNotValidException ex) throws IOException {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .limit(5)
                .map(fe -> fe.getField() + ": " +
                        (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value"))
                .collect(Collectors.joining("; "));

        log.warn("Validation failed - path={}, fields=[{}]", req.getRequestURI(), detail);

        writer.write(req, resp,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://syncnest.dev/problems/validation-error",
                "Validation Error",
                detail.isBlank() ? "Request validation failed." : detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public void handleConstraintViolation(@NonNull HttpServletRequest req,
                                          @NonNull HttpServletResponse resp,
                                          @NonNull ConstraintViolationException ex) throws IOException {
        String detail = ex.getConstraintViolations().stream()
                .limit(5)
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));

        log.warn("ConstraintViolation - path={}, violations=[{}]", req.getRequestURI(), detail);

        writer.write(req, resp,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://syncnest.dev/problems/validation-error",
                "Validation Error",
                detail.isBlank() ? "Request validation failed." : detail);
    }

    // -------------------------------------------------------------------------
    // 4. Malformed / missing request data
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleNotReadable(@NonNull HttpServletRequest req,
                                  @NonNull HttpServletResponse resp,
                                  @NonNull HttpMessageNotReadableException ex) throws IOException {
        log.warn("HttpMessageNotReadable - path={}: {}", req.getRequestURI(), ex.getMessage());
        writer.write(req, resp,
                HttpStatus.BAD_REQUEST,
                "https://syncnest.dev/problems/bad-request",
                "Bad Request",
                "Request body is missing or malformed JSON.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingParam(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse resp,
                                   @NonNull MissingServletRequestParameterException ex) throws IOException {
        log.warn("MissingRequestParam - path={}, param={}", req.getRequestURI(), ex.getParameterName());
        writer.write(req, resp,
                HttpStatus.BAD_REQUEST,
                "https://syncnest.dev/problems/missing-parameter",
                "Missing Parameter",
                "Required request parameter '" + ex.getParameterName() + "' is missing.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void handleTypeMismatch(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse resp,
                                   @NonNull MethodArgumentTypeMismatchException ex) throws IOException {
        log.warn("TypeMismatch - path={}, param={}", req.getRequestURI(), ex.getName());
        writer.write(req, resp,
                HttpStatus.BAD_REQUEST,
                "https://syncnest.dev/problems/type-mismatch",
                "Type Mismatch",
                "Parameter '" + ex.getName() + "' has an invalid type or value.");
    }

    // -------------------------------------------------------------------------
    // 5. HTTP-level errors
    // -------------------------------------------------------------------------

    @ExceptionHandler(NoHandlerFoundException.class)
    public void handleNoHandler(@NonNull HttpServletRequest req,
                                @NonNull HttpServletResponse resp,
                                @NonNull NoHandlerFoundException ex) throws IOException {
        log.warn("NoHandlerFound - {}", ex.getRequestURL());
        writer.write(req, resp,
                HttpStatus.NOT_FOUND,
                "https://syncnest.dev/problems/not-found",
                "Not Found",
                "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void handleMethodNotAllowed(@NonNull HttpServletRequest req,
                                       @NonNull HttpServletResponse resp,
                                       @NonNull HttpRequestMethodNotSupportedException ex) throws IOException {
        log.warn("MethodNotAllowed - path={}, method={}", req.getRequestURI(), ex.getMethod());
        writer.write(req, resp,
                HttpStatus.METHOD_NOT_ALLOWED,
                "https://syncnest.dev/problems/method-not-allowed",
                "Method Not Allowed",
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public void handleUnsupportedMediaType(@NonNull HttpServletRequest req,
                                           @NonNull HttpServletResponse resp,
                                           @NonNull HttpMediaTypeNotSupportedException ex) throws IOException {
        log.warn("UnsupportedMediaType - path={}, contentType={}", req.getRequestURI(), ex.getContentType());
        writer.write(req, resp,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "https://syncnest.dev/problems/unsupported-media-type",
                "Unsupported Media Type",
                "Content-Type '" + ex.getContentType() + "' is not supported. Use application/json.");
    }

    // -------------------------------------------------------------------------
    // 6. Data conflicts
    // -------------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public void handleDataIntegrity(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse resp,
                                    @NonNull DataIntegrityViolationException ex) throws IOException {
        log.warn("DataIntegrityViolation - path={}: {}", req.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        writer.write(req, resp,
                HttpStatus.CONFLICT,
                "https://syncnest.dev/problems/conflict",
                "Conflict",
                "A conflicting record already exists or a database constraint was violated.");
    }

    // -------------------------------------------------------------------------
    // 7. Fallback — catches anything not handled above
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public void handleGeneric(@NonNull HttpServletRequest req,
                              @NonNull HttpServletResponse resp,
                              @NonNull Exception ex) throws IOException {
        log.error("Unhandled exception - path={}, type={}",
                req.getRequestURI(), ex.getClass().getSimpleName(), ex);
        writer.write(req, resp,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "https://syncnest.dev/problems/internal-error",
                "Internal Server Error",
                "An unexpected error occurred. Please try again or contact support.");
    }
}