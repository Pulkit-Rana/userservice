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

        // 1) Extract Bearer token quickly
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        final String token = authHeader.substring(7);

        // 2) Reject blacklisted tokens early (saves DB lookups)
        if (blacklistService.isBlacklisted(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 3) Extract email (subject). This verifies signature via cached parser in JwtTokenProviderConfig.
            final String email = jwtService.extractEmail(token);
            if (!StringUtils.hasText(email)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 4) Only authenticate if context is empty
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load user by email (your UserDetailsService must map username->email)
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // 5) Validate token (includes expiry & subject match)
                if (jwtService.validateToken(token, userDetails)) {
                    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    securityContext.setAuthentication(authToken);
                    SecurityContextHolder.setContext(securityContext);
                }
            }
        } catch (IllegalArgumentException ex) {
            // Invalid token format/signature/claims → do not authenticate, just continue
            log.debug("JWT validation failed: {}", ex.getMessage());
        }

        // 6) Continue filter chain
        filterChain.doFilter(request, response);
    }
}
