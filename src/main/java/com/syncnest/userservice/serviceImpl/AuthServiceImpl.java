package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
import com.syncnest.userservice.dto.RefreshTokenResponse;
import com.syncnest.userservice.entity.User;
import com.syncnest.userservice.repository.UserRepository;
import com.syncnest.userservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProviderConfig jwtService;
    private final UserRepository userRepository;

    // NEW: inject refresh token service
    private final RefreshTokenServiceImpl refreshTokenService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) throws BadCredentialsException {
        // Basic input validation
        if (request == null) {
            throw new BadCredentialsException("Invalid email or password");
        }
        final String email = normalizeEmail(request.getEmail());
        final String password = request.getPassword();
        if (password == null || password.isBlank()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Authenticate credentials (uniform error on failure)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (org.springframework.security.core.AuthenticationException ex) {
            log.warn("Authentication failed for {}", email);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Fetch user and guard account state
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Hardening: block deleted/locked/disabled/unverified accounts
        if ((hasMethod(user, "isDeleted") && user.isDeleted())
                || (hasMethod(user, "isLocked") && user.isLocked())
                || !user.isEnabled()
                || (hasMethod(user, "isVerified") && !user.isVerified())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Optional: update last-login & default provider
        try {
            user.setLastLoginAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()));
        } catch (Exception ignored) { /* ignore if field not present */ }
        try {
            if (user.getProvider() == null) {
                user.setProvider(com.syncnest.userservice.entity.AuthProvider.LOCAL);
            }
        } catch (Exception ignored) { /* ignore if field not present */ }
        try {
            userRepository.saveAndFlush(user);
        } catch (Exception e) {
            log.warn("Non-fatal: unable to persist lastLogin/provider for {}", email, e);
        }

        // Build Access Token with minimal custom claims
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        try { claims.put("uid", user.getId() != null ? user.getId().toString() : null); } catch (Exception ignored) {}
        try { claims.put("role", user.getRole() != null ? user.getRole().name() : null); } catch (Exception ignored) {}
        final String accessToken = jwtService.generateToken(claims, email);

        // Issue refresh token (device-bound + max devices + sliding inactivity handled inside service)
        final com.syncnest.userservice.dto.RefreshTokenResponse rt =
                refreshTokenService.issue(user, request.getDeviceId());

        // Prepare response
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(accessToken);
        resp.setExpiresIn(jwtService.getTokenValiditySeconds());
        resp.setIssuedAt(java.time.Instant.now());
        if (rt != null) {
            resp.setRefreshToken(rt.getRefreshToken());
            resp.setDeviceId(rt.getDeviceId());
        }

        // User summary
        LoginResponse.UserSummary summary = new LoginResponse.UserSummary();
        try { summary.setId(user.getId() != null ? user.getId().toString() : null); } catch (Exception ignored) {}
        try { summary.setEmail(user.getEmail()); } catch (Exception ignored) {}
        try {
            // Prefer a friendly display name if available; fallback to username/email
            String displayName = null;
            try { displayName = (String) User.class.getMethod("getDisplayName").invoke(user); } catch (Exception ignored2) {}
            if (displayName == null || displayName.isBlank()) {
                try { displayName = user.getUsername(); } catch (Exception ignored2) {}
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = email;
            }
            summary.setDisplayName(displayName);
        } catch (Exception ignored) {}

        try {
            java.util.Set<String> roles = user.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.toSet());
            summary.setRoles(roles);
        } catch (Exception ignored) {}

        try {
            // Only if your entity exposes this
            if (hasMethod(user, "isVerified")) {
                summary.setEmailVerified((Boolean) User.class.getMethod("isVerified").invoke(user));
            }
        } catch (Exception ignored) {}

        resp.setUser(summary);
        return resp;
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new BadCredentialsException("Invalid email or password");
        return email.trim().toLowerCase();
    }

    private String safeId(UUID id) {
        return id != null ? id.toString() : null;
    }

    private boolean hasMethod(Object target, String method) {
        try {
            target.getClass().getMethod(method);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
