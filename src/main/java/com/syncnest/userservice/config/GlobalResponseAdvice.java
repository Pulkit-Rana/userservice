package com.syncnest.userservice.config;
import com.syncnest.userservice.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true; // wrap everything unless already wrapped
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> onBadCreds(Exception ex, HttpServletRequest req) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ApiResponse.<Void>builder()
                .success(false).code("UNAUTHORIZED").message("Invalid email or password")
                .timestamp(Instant.ofEpochMilli(System.currentTimeMillis())).build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> onValidation(MethodArgumentNotValidException ex) {
        return ApiResponse.<Void>builder()
                .success(false).code("VALIDATION_ERROR").message("Invalid request")
                .timestamp(Instant.ofEpochMilli(System.currentTimeMillis())).build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> onAny(Exception ex) {
        log.error("Server error", ex);
        return ApiResponse.<Void>builder()
                .success(false).code("INTERNAL_ERROR").message("Something went wrong")
                .timestamp(Instant.ofEpochMilli(System.currentTimeMillis())).build();
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        return null;
    }
}
