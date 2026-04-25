package com.syncnest.userservice.oauth2;

import com.syncnest.userservice.dto.DeviceContext;
import com.syncnest.userservice.dto.GoogleProfileSnapshot;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.entity.AuthProvider;
import com.syncnest.userservice.service.GoogleAuthService;
import com.syncnest.userservice.utils.RequestMetadataExtractor;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * Completes the server-side Google OAuth2 authorization-code flow: links/creates the local user,
 * issues refresh + access tokens, stores a short-lived OTT for the SPA to exchange for the JWT,
 * sets the HttpOnly refresh cookie, invalidates the temporary OAuth session, and redirects to the SPA.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.oauth2.frontend-success-url:http://localhost:5173/auth/google/callback}")
    private String frontendSuccessUrl;

    @Value("${app.oauth2.frontend-error-url:http://localhost:5173/auth/google/error}")
    private String frontendErrorUrl;

    @Value("${app.cookie.secure:false}")
    private boolean refreshCookieSecure;

    @Value("${refresh-token.expiration.milliseconds:2592000000}")
    private long refreshTokenExpirationMs;

    private final GoogleAuthService googleAuthService;
    private final RequestMetadataExtractor metadataExtractor;
    private final OAuth2ExchangeOttService oauth2ExchangeOttService;

    @PostConstruct
    void validateConfiguredUrls() {
        requireAbsoluteHttpUrl("app.oauth2.frontend-success-url", frontendSuccessUrl);
        requireAbsoluteHttpUrl("app.oauth2.frontend-error-url", frontendErrorUrl);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                          Authentication authentication) throws IOException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
                redirectWithError(request, response, "unsupported_principal");
                return;
            }
            if (!"google".equalsIgnoreCase(oauth2Token.getAuthorizedClientRegistrationId())) {
                redirectWithError(request, response, "unsupported_provider");
                return;
            }
            OAuth2User oauth2User = oauth2Token.getPrincipal();
            String email = normalizeEmail(oauth2User.getAttribute("email"));
            String sub = oauth2User.getName();
            if (!StringUtils.hasText(sub)) {
                sub = oauth2User.getAttribute("sub");
            }
            boolean verified = parseEmailVerified(oauth2User);

            String ua = metadataExtractor.extractUserAgent(request);
            DeviceContext ctx = DeviceContext.builder()
                    .ip(metadataExtractor.extractIp(request))
                    .userAgent(ua)
                    .os(metadataExtractor.parseOs(ua))
                    .browser(metadataExtractor.parseBrowser(ua))
                    .deviceType(metadataExtractor.parseDeviceType(ua))
                    .provider(AuthProvider.GOOGLE)
                    .build();

            GoogleProfileSnapshot profile = GoogleProfileSnapshot.fromOAuth2User(oauth2User);
            LoginResponse login = googleAuthService.loginWithVerifiedGoogleClaims(email, sub, verified, ctx, profile);
            String refresh = login.getRefreshToken();
            if (!StringUtils.hasText(refresh)) {
                log.error("Google OAuth login succeeded but refresh token is missing");
                redirectWithError(request, response, "token_issue_failed");
                return;
            }
            if (login.getUser() == null || !StringUtils.hasText(login.getUser().getId())) {
                log.error("Google OAuth login succeeded but user summary is missing");
                redirectWithError(request, response, "token_issue_failed");
                return;
            }

            UUID userId = UUID.fromString(login.getUser().getId());
            String ott = oauth2ExchangeOttService.createForUser(userId);

            ResponseCookie cookie = ResponseCookie.from("refreshToken", refresh)
                    .httpOnly(true)
                    .secure(refreshCookieSecure)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                    .build();
            response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            login.setRefreshToken(null);

            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) {
                request.getSession().invalidate();
            }

            URI target = UriComponentsBuilder.fromUriString(frontendSuccessUrl)
                    .replaceQueryParam("ott", ott)
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();
            response.sendRedirect(target.toString());
            log.info("Google OAuth2 browser flow completed, redirect to SPA for OTT exchange");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Google OAuth2 success handling failed: {}", ex.toString());
            redirectWithError(request, response, "login_failed");
        }
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response, String errorCode)
            throws IOException {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
        URI target = UriComponentsBuilder.fromUriString(frontendErrorUrl)
                .replaceQueryParam("error", errorCode)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        response.sendRedirect(target.toString());
    }

    private static void requireAbsoluteHttpUrl(String name, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " must be configured");
        }
        URI uri = URI.create(value.trim());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(name + " must use http or https scheme");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(name + " must include a host");
        }
    }

    private static String normalizeEmail(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    private static boolean parseEmailVerified(OAuth2User user) {
        Object v = user.getAttribute("email_verified");
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return false;
    }
}
