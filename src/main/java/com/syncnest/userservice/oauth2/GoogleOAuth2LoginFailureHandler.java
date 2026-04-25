package com.syncnest.userservice.oauth2;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Redirects the browser to the SPA after a failed Google OAuth2 login (no sensitive details in the URL).
 */
@Component
@Slf4j
public class GoogleOAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.oauth2.frontend-error-url:http://localhost:5173/auth/google/error}")
    private String frontendErrorUrl;

    @PostConstruct
    void validateConfiguredUrl() {
        URI uri = URI.create(frontendErrorUrl.trim());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("app.oauth2.frontend-error-url must use http or https");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException("app.oauth2.frontend-error-url must include a host");
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google OAuth2 login failed: {}", exception.getClass().getSimpleName());
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
        URI target = UriComponentsBuilder.fromUriString(frontendErrorUrl)
                .replaceQueryParam("error", "oauth2_failed")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        response.sendRedirect(target.toString());
    }
}
