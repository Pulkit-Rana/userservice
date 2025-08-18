package com.syncnest.userservice.SecurityConfig;

import com.syncnest.userservice.utils.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Sends 403 Forbidden for authenticated requests that lack permission.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter writer;

    public JwtAccessDeniedHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(@NonNull HttpServletRequest request,
                       @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException accessDeniedException) {
        try {
            writer.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "https://syncnest.dev/problems/forbidden",
                    "Forbidden",
                    "You do not have permission to access this resource."
            );
        } catch (Exception ignored) {
            // Safe no-op if response is already committed
        }
    }
}
