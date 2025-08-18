package com.syncnest.userservice.SecurityConfig;

import com.syncnest.userservice.utils.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Sends 401 Unauthorized for unauthenticated requests.
 * Adds RFC 6750 WWW-Authenticate hint when the client attempted Bearer auth.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter writer;

    public JwtAuthenticationEntryPoint(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(@NonNull HttpServletRequest request,
                         @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException authException) {
        try {
            String ah = request.getHeader("Authorization");
            if (ah != null && ah.startsWith("Bearer ")) {
                // Minimal hint for clients; do not leak details
                response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            }

            writer.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "https://syncnest.dev/problems/unauthorized",
                    "Unauthorized",
                    "Authentication is required to access this resource."
            );
        } catch (Exception ignored) {
            // If the client closed the connection, ignore
        }
    }
}
