package com.syncnest.userservice.serviceImpl;

import com.syncnest.userservice.SecurityConfig.JwtTokenProviderConfig;
import com.syncnest.userservice.dto.LoginRequest;
import com.syncnest.userservice.dto.LoginResponse;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProviderConfig jwt;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        final String email = normalizeIdentifierAsEmail(request.getIdentifier());

        // 1) Single indexed lookup (unique email)
        final User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // 2) Account gates (generic failure to avoid user enumeration)
        if (user.isDeleted() || user.isLocked() || !user.isVerified() || !user.isEnabled()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // 3) Password verification via AuthenticationManager (BCrypt via DaoAuthenticationProvider)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // (Optional) 4) OTP check — only if your flow requires it here.
        // Iske bina bhi login secure rehta hai (password + account gates).
        // Agar aapke paas OTP/TOTP validator service wired hai, yahan call karein.
        // Example:
        // if (user.isMfaEnabled()) {
        //     if (request.getOtp() == null || !otpValidator.isValid(user.getId(), request.getOtp())) {
        //         throw new BadCredentialsException("Invalid credentials");
        //     }
        // }

        // 5) Minimal signed claims; subject = email for resource servers
        final Map<String, Object> claims = Map.of(
                "uid", toStringOrEmpty(user.getId()),
                "role", primaryRole(user),
                "mfa", user.isMfaEnabled()
        );

        final String accessToken = jwt.generateToken(claims, email);

        // 6) Build DTO response (refreshToken set to null unless you implement refresh-flow)
        final LoginResponse res = new LoginResponse();
        res.setAccessToken(accessToken);
        res.setExpiresIn(jwt.getTokenValiditySeconds());
        res.setRefreshToken(null); // implement if you have refresh tokens
        res.setIssuedAt(Instant.now());
        res.setUser(toUserSummary(user));

        // (Optional) structured audit/security logs without leaking specifics
        safeAuditLog(user.getId(), request.getClientId(), request.getDeviceId(), request.getIp(), request.getUserAgent());

        return res;
    }

    // --- helpers ---

    private String normalizeIdentifierAsEmail(String identifier) {
        if (identifier == null) throw new BadCredentialsException("Invalid credentials");
        final String normalized = identifier.trim().toLowerCase();
        // If you support username/phone, branch here. Current contract: treat as email.
        return normalized;
    }

    private String toStringOrEmpty(UUID id) {
        return Optional.ofNullable(id).map(UUID::toString).orElse("");
    }

    private String primaryRole(User user) {
        // If your entity has a single Role enum: user.getRole().name()
        // If it supports multiple authorities, return a deterministic primary (e.g., first or highest).
        try {
            return user.getRole().name();
        } catch (Exception ignore) {
            return "USER";
        }
    }

    private LoginResponse.UserSummary toUserSummary(User user) {
        final LoginResponse.UserSummary summary = new LoginResponse.UserSummary();
        summary.setId(toStringOrEmpty(user.getId()));
        summary.setEmail(user.getEmail());
        summary.setDisplayName(safeDisplayName(user));
        summary.setRoles(resolveRoles(user));
        summary.setEmailVerified(user.isVerified());
        summary.setMfaEnabled(user.isMfaEnabled());
        return summary;
    }

    private String safeDisplayName(User user) {
        // Prefer full name if present, else fallback to email prefix
        final String name = (user.getFirstName() != null ? user.getFirstName() : "")
                + (user.getLastName() != null ? " " + user.getLastName() : "");
        final String trimmed = name.trim();
        if (!trimmed.isEmpty()) return trimmed;
        final String email = user.getEmail();
        return (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : "User";
    }

    private Set<String> resolveRoles(User user) {
        // Single role fallback
        try {
            final String single = user.getRole().name();
            final Set<String> roles = new HashSet<>(1);
            roles.add(single);
            return roles;
        } catch (Exception e) {
            return Set.of("USER");
        }
    }

    private void safeAuditLog(UUID userId, String clientId, String deviceId, String ip, String userAgent) {
        try {
            log.info("auth.login success uid={} clientId={} deviceId={} ip={} ua={}",
                    toStringOrEmpty(userId), nullSafe(clientId), nullSafe(deviceId), nullSafe(ip), nullSafe(userAgent));
        } catch (Exception ignore) {
            // never break login on logging
        }
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
}
