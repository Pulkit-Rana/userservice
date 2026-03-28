package com.syncnest.userservice.SecurityConfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilterConfig extends OncePerRequestFilter {

    private final JwtTokenProviderConfig jwtService;                 // previously JwtServiceConfiguration
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistConfig blacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, java.io.IOException {

        String path = request.getRequestURI();
        log.debug("JWT auth filter processing: {} {}", request.getMethod(), path);

        // 1) Extract Bearer token quickly
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token found in Authorization header for {} {}", request.getMethod(), path);
            filterChain.doFilter(request, response);
            return;
        }
        final String token = authHeader.substring(7);
        log.debug("Bearer token extracted, length: {}", token.length());

        // 2) Reject blacklisted tokens early (saves DB lookups)
        if (blacklistService.isBlacklisted(token)) {
            log.warn("Blacklisted token attempted for {} {}", request.getMethod(), path);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 3) Extract email (subject). This verifies signature via cached parser in JwtTokenProviderConfig.
            final String email = jwtService.extractEmail(token);
            if (!StringUtils.hasText(email)) {
                log.warn("JWT token has no email subject for {} {}", request.getMethod(), path);
                filterChain.doFilter(request, response);
                return;
            }

            log.debug("JWT email extracted: {}", maskEmail(email));

            // 4) Only authenticate if context is empty
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                log.debug("Authenticating user from JWT: {}", maskEmail(email));
                // Load user by email (your UserDetailsService must map username->email)
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                log.debug("User details loaded for: {}", maskEmail(email));

                // 5) Validate token (includes expiry & subject match)
                if (jwtService.validateToken(token, userDetails)) {
                    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    securityContext.setAuthentication(authToken);
                    SecurityContextHolder.setContext(securityContext);
                    log.info("JWT authentication successful for: {} on {} {}", maskEmail(email), request.getMethod(), path);
                } else {
                    log.warn("JWT validation failed for: {} on {} {}", maskEmail(email), request.getMethod(), path);
                }
            } else {
                log.debug("Security context already populated, skipping JWT authentication");
            }
        } catch (UsernameNotFoundException e) {
            // User vanished or is unknown — proceed unauthenticated
            log.warn("User not found for JWT subject: {}", e.getMessage());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // Signature/format/expiry/etc. — proceed unauthenticated
            log.warn("JWT invalid: {} - Message: {}", e.getClass().getSimpleName(), e.getMessage());
        }


        // 6) Continue filter chain
        filterChain.doFilter(request, response);
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 3) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@***";
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
