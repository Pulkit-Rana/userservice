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

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorResponseWriter writer;

    // ---------- Custom domain / API exceptions ----------

    @ExceptionHandler(ApiException.class)
    public void handleApiException(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse resp,
                                   @NonNull ApiException ex) throws IOException {
        // Log minimal info; details remain generic to clients
        log.debug("ApiException: status={}, type={}, title={}, detail={}",
                ex.getStatus(), ex.getType(), ex.getTitle(), ex.getMessage());
        writer.write(req, resp, ex.getStatus(), ex.getType(), ex.getTitle(), safeDetail(ex.getMessage()));
    }

    // ---------- Validation & request-shape errors ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleMethodArgumentNotValid(@NonNull HttpServletRequest req,
                                             @NonNull HttpServletResponse resp,
                                             @NonNull MethodArgumentNotValidException ex) throws IOException {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .limit(5) // keep payload small
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));
        writer.write(req, resp, HttpStatus.UNPROCESSABLE_ENTITY,
                "https://syncnest.dev/problems/validation-error",
                "Validation Error",
                details.isBlank() ? "Request validation failed." : details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public void handleConstraintViolation(@NonNull HttpServletRequest req,
                                          @NonNull HttpServletResponse resp,
                                          @NonNull ConstraintViolationException ex) throws IOException {
        var details = ex.getConstraintViolations().stream()
                .limit(5)
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        writer.write(req, resp, HttpStatus.UNPROCESSABLE_ENTITY,
                "https://syncnest.dev/problems/validation-error",
                "Validation Error",
                details.isBlank() ? "Request validation failed." : details);
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, HttpMessageNotReadableException.class })
    public void handleBadRequest(@NonNull HttpServletRequest req,
                                 @NonNull HttpServletResponse resp,
                                 @NonNull Exception ex) throws IOException {
        writer.write(req, resp, HttpStatus.BAD_REQUEST,
                "https://syncnest.dev/problems/bad-request",
                "Bad Request",
                "Malformed or missing request parameters.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void handleTypeMismatch(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse resp,
                                   @NonNull MethodArgumentTypeMismatchException ex) throws IOException {
        writer.write(req, resp, HttpStatus.BAD_REQUEST,
                "https://syncnest.dev/problems/type-mismatch",
                "Type Mismatch",
                "Parameter '" + ex.getName() + "' has invalid type.");
    }

    // ---------- HTTP mapping errors (JSON, not HTML) ----------

    @ExceptionHandler(NoHandlerFoundException.class)
    public void handleNoHandler(@NonNull HttpServletRequest req,
                                @NonNull HttpServletResponse resp,
                                @NonNull NoHandlerFoundException ex) throws IOException {
        writer.write(req, resp, HttpStatus.NOT_FOUND,
                "https://syncnest.dev/problems/not-found",
                "Not Found",
                "No handler for " + ex.getRequestURL());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void handleMethodNotAllowed(@NonNull HttpServletRequest req,
                                       @NonNull HttpServletResponse resp,
                                       @NonNull HttpRequestMethodNotSupportedException ex) throws IOException {
        writer.write(req, resp, HttpStatus.METHOD_NOT_ALLOWED,
                "https://syncnest.dev/problems/method-not-allowed",
                "Method Not Allowed",
                "HTTP method not supported for this endpoint.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public void handleUnsupportedMediaType(@NonNull HttpServletRequest req,
                                           @NonNull HttpServletResponse resp,
                                           @NonNull HttpMediaTypeNotSupportedException ex) throws IOException {
        writer.write(req, resp, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "https://syncnest.dev/problems/unsupported-media-type",
                "Unsupported Media Type",
                "Content type is not supported.");
    }

    // ---------- Data conflicts ----------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public void handleDataIntegrity(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse resp,
                                    @NonNull DataIntegrityViolationException ex) throws IOException {
        ex.getMostSpecificCause();
        log.debug("DataIntegrityViolation: {}", ex.getMostSpecificCause().getMessage());
        writer.write(req, resp, HttpStatus.CONFLICT,
                "https://syncnest.dev/problems/conflict",
                "Conflict",
                "A conflicting resource already exists or violates a constraint.");
    }

    // ---------- Fallback 500 ----------

    @ExceptionHandler(Exception.class)
    public void handleGeneric(@NonNull HttpServletRequest req,
                              @NonNull HttpServletResponse resp,
                              @NonNull Exception ex) throws IOException {
        // Log full for ops; respond generic to the client
        log.error("Unhandled exception", ex);
        writer.write(req, resp, HttpStatus.INTERNAL_SERVER_ERROR,
                "https://syncnest.dev/problems/internal-error",
                "Internal Server Error",
                "An unexpected error occurred.");
    }

    // ---------- helpers ----------

    private String safeDetail(String s) {
        return (s == null || s.isBlank()) ? "Request could not be processed." : s;
    }
}
