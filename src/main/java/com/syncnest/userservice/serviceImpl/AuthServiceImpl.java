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
        final String email = normalizeEmail(request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            log.debug("Authentication failed for {}: {}", email, ex.getMessage());
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (user.isDeleted() || user.isLocked() || !user.isEnabled() || !user.isVerified()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        user.setLastLoginAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()));
        if (user.getProvider() == null) {
            user.setProvider(com.syncnest.userservice.entity.AuthProvider.LOCAL);
        }
        userRepository.saveAndFlush(user);

        // Access token
        var claims = new java.util.HashMap<String, Object>();
        claims.put("uid", safeId(user.getId()));
        claims.put("role", user.getRole().name());
        String accessToken = jwtService.generateToken(claims, email);

        // NEW: Issue refresh token bound to deviceId (enforces 3 devices + sliding inactivity)
        RefreshTokenResponse rt = refreshTokenService.issue(user, request.getDeviceId());

        // Build response
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(accessToken);
        resp.setExpiresIn(jwtService.getTokenValiditySeconds());
        resp.setIssuedAt(Instant.now());

        // User summary (existing)
        LoginResponse.UserSummary summary = new LoginResponse.UserSummary();
        summary.setId(safeId(user.getId()));
        summary.setEmail(user.getEmail());
        summary.setDisplayName(user.getEmail());
        summary.setRoles(Set.of(user.getRole().name()));
        summary.setEmailVerified(user.isVerified());
        resp.setUser(summary);

        // NEW: include refresh token details in LoginResponse (if your DTO supports fields)
        // If LoginResponse doesn't have fields, return only from controller as cookie.
        resp.setRefreshToken(rt.getRefreshToken()); // raw token for client/cookie
        resp.setExpiresIn(rt.getExpiresAt()); // if you have this field
        resp.getDeviceId(rt.getDeviceId()); // if you have this field

        return resp;
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new BadCredentialsException("Invalid email or password");
        return email.trim().toLowerCase();
    }

    private String safeId(UUID id) {
        return id != null ? id.toString() : null;
    }
}
